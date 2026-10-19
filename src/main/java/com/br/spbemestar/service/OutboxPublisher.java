package com.br.spbemestar.service;

import com.br.spbemestar.config.OutboxProperties;
import com.br.spbemestar.config.RabbitConfig;
import com.br.spbemestar.model.OutboxEvent;
import com.br.spbemestar.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Publica no RabbitMQ os eventos gravados na outbox. Um evento só é marcado como publicado
 * depois da confirmação do broker; se a confirmação não vier, ele é reenviado no próximo ciclo.
 * A entrega é "pelo menos uma vez": os consumidores usam o {@code messageId} (id do evento)
 * para descartar duplicatas.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutboxEventRepository outbox;
    private final RabbitTemplate rabbit;
    private final OutboxProperties properties;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${app.outbox.publish-interval-ms:1000}",
            initialDelayString = "${app.outbox.publish-interval-ms:1000}")
    @Transactional
    public int publishPending() {
        List<OutboxEvent> batch = outbox.lockUnpublished(properties.batchSize());
        int published = 0;
        for (OutboxEvent event : batch) {
            // Para no primeiro erro para não publicar fora de ordem; o restante fica para o próximo ciclo
            if (!send(event)) {
                break;
            }
            event.markPublished(Instant.now(clock));
            published++;
        }
        if (published > 0) {
            log.debug("{} eventos da outbox publicados", published);
        }
        return published;
    }

    private boolean send(OutboxEvent event) {
        String id = event.getId().toString();
        Message message = MessageBuilder.withBody(event.getPayload().getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding(StandardCharsets.UTF_8.name())
                .setMessageId(id)
                .setType(event.getType())
                .setTimestamp(Date.from(event.getCreatedAt()))
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .build();
        CorrelationData correlation = new CorrelationData(id);

        try {
            rabbit.send(RabbitConfig.EVENTS_EXCHANGE, event.getType(), message, correlation);
            CorrelationData.Confirm confirm = correlation.getFuture()
                    .get(properties.confirmTimeout().toMillis(), TimeUnit.MILLISECONDS);
            if (!confirm.ack()) {
                log.warn("Broker recusou o evento {} ({}): {}", id, event.getType(), confirm.reason());
                return false;
            }
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException | TimeoutException | RuntimeException e) {
            log.warn("Falha ao publicar o evento {} ({}); nova tentativa no próximo ciclo", id, event.getType(), e);
            return false;
        }
    }
}
