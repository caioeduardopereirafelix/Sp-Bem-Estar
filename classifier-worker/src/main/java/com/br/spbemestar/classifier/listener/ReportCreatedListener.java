package com.br.spbemestar.classifier.listener;

import com.br.spbemestar.classifier.config.ClassifierProperties;
import com.br.spbemestar.classifier.config.RabbitConfig;
import com.br.spbemestar.classifier.dto.ReportClassified;
import com.br.spbemestar.classifier.dto.ReportCreated;
import com.br.spbemestar.classifier.exception.InvalidPhotoException;
import com.br.spbemestar.classifier.service.Classification;
import com.br.spbemestar.classifier.service.GridFsPhotoReader;
import com.br.spbemestar.classifier.service.PhotoClassifier;
import com.br.spbemestar.classifier.service.ResultPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Consome os relatos novos, classifica a foto e publica o resultado. Mensagens que nunca vão dar
 * certo (JSON inválido, foto inexistente ou ilegível) vão direto para a DLQ; as demais falhas são
 * tentadas de novo conforme {@code spring.rabbitmq.listener.simple.retry}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportCreatedListener {

    private final JsonMapper json;
    private final GridFsPhotoReader photos;
    private final PhotoClassifier classifier;
    private final ResultPublisher publisher;
    private final ClassifierProperties properties;

    @RabbitListener(queues = RabbitConfig.CLASSIFICATION_QUEUE)
    public void onReportCreated(Message message) {
        String messageId = message.getMessageProperties().getMessageId();
        if (messageId == null) {
            throw new AmqpRejectAndDontRequeueException("Mensagem sem messageId");
        }
        ReportCreated event = parse(message);

        byte[] photo = photos.read(event.photoId()).orElseThrow(() ->
                new AmqpRejectAndDontRequeueException("Foto %s do relato %d não encontrada"
                        .formatted(event.photoId(), event.reportId())));

        Classification classification;
        try {
            classification = classifier.classify(photo);
        } catch (InvalidPhotoException e) {
            throw new AmqpRejectAndDontRequeueException("Foto do relato %d ilegível: %s"
                    .formatted(event.reportId(), e.getMessage()), e);
        }

        publisher.publish(messageId, new ReportClassified(
                event.reportId(), classification.label(), classification.confidence(), properties.modelVersion()));
        log.info("Relato {} ({}) classificado como {} ({})", event.reportId(), event.category(),
                classification.label(), "%.3f".formatted(classification.confidence()));
    }

    private ReportCreated parse(Message message) {
        ReportCreated event;
        try {
            event = json.readValue(message.getBody(), ReportCreated.class);
        } catch (JacksonException e) {
            throw new AmqpRejectAndDontRequeueException("Evento ReportCreated inválido", e);
        }
        if (event.reportId() == null || event.photoId() == null) {
            throw new AmqpRejectAndDontRequeueException("Evento ReportCreated sem reportId ou photoId");
        }
        return event;
    }
}
