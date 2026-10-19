package com.br.spbemestar.listener;

import com.br.spbemestar.config.RabbitConfig;
import com.br.spbemestar.dto.ReportClassifiedMessage;
import com.br.spbemestar.exception.ResourceNotFoundException;
import com.br.spbemestar.model.ClassificationStatus;
import com.br.spbemestar.service.ClassificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Recebe o resultado do classifier-worker. Mensagem inválida ou de relato inexistente vai direto
 * para a DLQ; falhas transitórias (ex.: banco fora do ar) são tentadas de novo.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClassificationResultListener {

    private final JsonMapper json;
    private final ClassificationService classification;

    @RabbitListener(queues = RabbitConfig.RESULT_QUEUE)
    public void onReportClassified(Message message) {
        ReportClassifiedMessage result = parse(message);
        ClassificationStatus status;
        try {
            status = classification.apply(result.reportId(), result.predictedCategory(), result.confidence());
        } catch (ResourceNotFoundException e) {
            throw new AmqpRejectAndDontRequeueException(e.getMessage(), e);
        }

        if (status == null) {
            log.debug("Resultado repetido para o relato {}; ignorado", result.reportId());
        } else if (status != ClassificationStatus.AGREED) {
            log.info("Relato {} marcado para moderação: {} (previsto {}, confiança {}, modelo {})",
                    result.reportId(), status, result.predictedCategory(), result.confidence(), result.modelVersion());
        }
    }

    private ReportClassifiedMessage parse(Message message) {
        ReportClassifiedMessage result;
        try {
            result = json.readValue(message.getBody(), ReportClassifiedMessage.class);
        } catch (JacksonException e) {
            throw new AmqpRejectAndDontRequeueException("Resultado de classificação inválido", e);
        }
        if (result.reportId() == null || result.predictedCategory() == null || result.predictedCategory().isBlank()
                || result.confidence() == null || result.confidence() < 0 || result.confidence() > 1) {
            throw new AmqpRejectAndDontRequeueException("Resultado de classificação incompleto: " + result);
        }
        return result;
    }
}
