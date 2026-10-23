package com.br.spbemestar.classifier.listener;

import com.br.spbemestar.classifier.config.ClassifierProperties;
import com.br.spbemestar.classifier.config.RabbitConfig;
import com.br.spbemestar.classifier.dto.ReportClassified;
import com.br.spbemestar.classifier.dto.ReportCreated;
import com.br.spbemestar.classifier.exception.InvalidPhotoException;
import com.br.spbemestar.classifier.exception.RejectedReportException;
import com.br.spbemestar.classifier.service.Classification;
import com.br.spbemestar.classifier.service.GridFsPhotoReader;
import com.br.spbemestar.classifier.service.PhotoClassifier;
import com.br.spbemestar.classifier.service.ResultPublisher;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final MeterRegistry meters;

    @RabbitListener(queues = RabbitConfig.CLASSIFICATION_QUEUE)
    public void onReportCreated(Message message) {
        String messageId = message.getMessageProperties().getMessageId();
        if (messageId == null) {
            throw rejected("invalid_message", "Mensagem sem messageId", null);
        }
        ReportCreated event = parse(message);

        byte[] photo = photos.read(event.photoId()).orElseThrow(() -> rejected("photo_missing",
                "Foto %s do relato %d não encontrada".formatted(event.photoId(), event.reportId()), null));

        Classification classification;
        Timer.Sample inference = Timer.start(meters);
        try {
            classification = classifier.classify(photo);
        } catch (InvalidPhotoException e) {
            throw rejected("photo_unreadable", "Foto do relato %d ilegível: %s"
                    .formatted(event.reportId(), e.getMessage()), e);
        } finally {
            inference.stop(Timer.builder("classifier.inference")
                    .description("Tempo para classificar uma foto com o modelo ONNX")
                    .tag("model", properties.modelVersion())
                    .publishPercentileHistogram()
                    .register(meters));
        }

        publisher.publish(messageId, new ReportClassified(
                event.reportId(), classification.label(), classification.confidence(), properties.modelVersion()));
        meters.counter("classifier.reports", "outcome", "classified", "label", classification.label()).increment();
        // a distribuição da confiança mostra quando o modelo começa a errar (fotos diferentes das do treino)
        DistributionSummary.builder("classifier.confidence")
                .description("Confiança do modelo na categoria prevista (0 a 1)")
                .tag("model", properties.modelVersion())
                .serviceLevelObjectives(0.6, 0.8, 0.9)
                .register(meters)
                .record(classification.confidence());
        log.info("Relato {} ({}) classificado como {} ({})", event.reportId(), event.category(),
                classification.label(), "%.3f".formatted(classification.confidence()));
    }

    private ReportCreated parse(Message message) {
        ReportCreated event;
        try {
            event = json.readValue(message.getBody(), ReportCreated.class);
        } catch (JacksonException e) {
            throw rejected("invalid_message", "Evento ReportCreated inválido", e);
        }
        if (event.reportId() == null || event.photoId() == null) {
            throw rejected("invalid_message", "Evento ReportCreated sem reportId ou photoId", null);
        }
        return event;
    }

    /** Falha definitiva: a mensagem vai para a DLQ, e a métrica (no RabbitConfig) diz por quê. */
    private RejectedReportException rejected(String reason, String message, Throwable cause) {
        return new RejectedReportException(reason, message, cause);
    }
}
