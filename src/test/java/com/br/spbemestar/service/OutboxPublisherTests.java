package com.br.spbemestar.service;

import com.br.spbemestar.TestPhotos;
import com.br.spbemestar.TestcontainersConfiguration;
import com.br.spbemestar.config.RabbitConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Intervalo longo para o agendamento não disputar os eventos com o teste
@SpringBootTest(properties = "app.outbox.publish-interval-ms=3600000")
@Import(TestcontainersConfiguration.class)
class OutboxPublisherTests {

    @Autowired OutboxPublisher publisher;
    @Autowired ReportService reportService;
    @Autowired RabbitTemplate rabbit;
    @Autowired AmqpAdmin admin;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper json;

    @BeforeEach
    void drainPending() {
        publisher.publishPending();
        admin.purgeQueue(RabbitConfig.CLASSIFICATION_QUEUE, false);
        admin.purgeQueue(RabbitConfig.CLASSIFICATION_DLQ, false);
    }

    @Test
    void reportCreatedIsPublishedOnlyOnce() {
        Long reportId = reportService.create("POTHOLE", -22.00, -46.00, TestPhotos.jpeg()).reportId();
        UUID eventId = eventIdOf(reportId);

        assertThat(publisher.publishPending()).isEqualTo(1);
        assertThat(publisher.publishPending()).isZero();

        assertThat(jdbc.queryForObject("SELECT published_at IS NOT NULL FROM outbox_event WHERE id = ?",
                Boolean.class, eventId)).isTrue();

        List<Message> messages = drain(RabbitConfig.CLASSIFICATION_QUEUE);
        assertThat(messages).hasSize(1);
        MessageProperties props = messages.getFirst().getMessageProperties();
        assertThat(props.getMessageId()).isEqualTo(eventId.toString());
        assertThat(props.getType()).isEqualTo(ReportService.REPORT_CREATED);
        assertThat(props.getContentType()).isEqualTo(MessageProperties.CONTENT_TYPE_JSON);
        assertThat(json.readTree(messages.getFirst().getBody()).get("reportId").asLong()).isEqualTo(reportId);
    }

    @Test
    void eventsArePublishedInCreationOrder() {
        Long first = reportService.create("TRASH", -22.10, -46.00, TestPhotos.jpeg()).reportId();
        Long second = reportService.create("TRASH", -22.20, -46.00, TestPhotos.jpeg()).reportId();

        publisher.publishPending();

        assertThat(drain(RabbitConfig.CLASSIFICATION_QUEUE))
                .extracting(m -> m.getMessageProperties().getMessageId())
                .containsExactly(eventIdOf(first).toString(), eventIdOf(second).toString());
    }

    @Test
    void rejectedMessagesGoToDeadLetterQueue() {
        Long reportId = reportService.create("POTHOLE", -22.30, -46.00, TestPhotos.jpeg()).reportId();
        publisher.publishPending();

        rabbit.execute(channel -> {
            var delivery = channel.basicGet(RabbitConfig.CLASSIFICATION_QUEUE, false);
            channel.basicReject(delivery.getEnvelope().getDeliveryTag(), false);
            return null;
        });

        Message dead = rabbit.receive(RabbitConfig.CLASSIFICATION_DLQ, 5000);
        assertThat(dead).isNotNull();
        assertThat(dead.getMessageProperties().getMessageId()).isEqualTo(eventIdOf(reportId).toString());
        assertThat(rabbit.receive(RabbitConfig.CLASSIFICATION_QUEUE)).isNull();
    }

    private UUID eventIdOf(Long reportId) {
        return jdbc.queryForObject("SELECT id FROM outbox_event WHERE type = ? AND aggregate_id = ?",
                UUID.class, ReportService.REPORT_CREATED, reportId);
    }

    private List<Message> drain(String queue) {
        List<Message> messages = new ArrayList<>();
        Message message;
        while ((message = rabbit.receive(queue, 500)) != null) {
            messages.add(message);
        }
        return messages;
    }
}
