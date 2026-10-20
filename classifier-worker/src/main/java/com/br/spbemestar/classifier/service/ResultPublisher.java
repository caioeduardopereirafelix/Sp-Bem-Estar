package com.br.spbemestar.classifier.service;

import com.br.spbemestar.classifier.config.ClassifierProperties;
import com.br.spbemestar.classifier.config.RabbitConfig;
import com.br.spbemestar.classifier.dto.ReportClassified;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ResultPublisher {

    private final RabbitTemplate rabbit;
    private final JsonMapper json;
    private final ClassifierProperties properties;

    /**
     * Publica o resultado e espera o ack do broker; só então a mensagem de entrada é confirmada.
     * O id do resultado deriva do id do evento de origem, então uma reentrega gera a mesma mensagem
     * e a API consegue descartá-la.
     */
    public void publish(String sourceMessageId, ReportClassified result) {
        String id = UUID.nameUUIDFromBytes((RabbitConfig.REPORT_CLASSIFIED + ":" + sourceMessageId)
                .getBytes(StandardCharsets.UTF_8)).toString();
        Message message = MessageBuilder.withBody(json.writeValueAsBytes(result))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding(StandardCharsets.UTF_8.name())
                .setMessageId(id)
                .setType(RabbitConfig.REPORT_CLASSIFIED)
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .build();

        rabbit.invoke(ops -> {
            ops.send(RabbitConfig.EVENTS_EXCHANGE, RabbitConfig.REPORT_CLASSIFIED, message);
            ops.waitForConfirmsOrDie(properties.confirmTimeout().toMillis());
            return null;
        });
    }
}
