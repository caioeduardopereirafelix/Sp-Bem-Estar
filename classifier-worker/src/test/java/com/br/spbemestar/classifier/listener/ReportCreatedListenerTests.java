package com.br.spbemestar.classifier.listener;

import com.br.spbemestar.classifier.TestImages;
import com.br.spbemestar.classifier.TestcontainersConfiguration;
import com.br.spbemestar.classifier.config.RabbitConfig;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "classifier.model=classpath:models/color-test.onnx",
        "classifier.model-version=color-test",
        "spring.rabbitmq.listener.simple.retry.initial-interval=100ms"
})
@Import(TestcontainersConfiguration.class)
class ReportCreatedListenerTests {

    private static final long TIMEOUT_MS = 10_000;

    @Autowired RabbitTemplate rabbit;
    @Autowired AmqpAdmin admin;
    @Autowired GridFsTemplate gridFs;
    @Autowired JsonMapper json;

    @BeforeEach
    void purge() {
        admin.purgeQueue(RabbitConfig.RESULT_QUEUE, false);
        admin.purgeQueue(RabbitConfig.CLASSIFICATION_DLQ, false);
    }

    @Test
    void classifiesThePhotoAndPublishesTheResult() {
        String photoId = storePhoto(TestImages.jpeg(Color.RED));
        String eventId = UUID.randomUUID().toString();

        sendReportCreated(eventId, 42L, photoId);

        Message result = rabbit.receive(RabbitConfig.RESULT_QUEUE, TIMEOUT_MS);
        assertThat(result).isNotNull();
        MessageProperties props = result.getMessageProperties();
        assertThat(props.getType()).isEqualTo(RabbitConfig.REPORT_CLASSIFIED);
        assertThat(props.getMessageId()).isEqualTo(UUID.nameUUIDFromBytes(
                ("ReportClassified:" + eventId).getBytes(StandardCharsets.UTF_8)).toString());

        JsonNode body = json.readTree(result.getBody());
        assertThat(body.get("reportId").asLong()).isEqualTo(42L);
        assertThat(body.get("predictedCategory").asString()).isEqualTo("POTHOLE");
        assertThat(body.get("confidence").asDouble()).isGreaterThan(0.99);
        assertThat(body.get("modelVersion").asString()).isEqualTo("color-test");
    }

    @Test
    void redeliveryProducesTheSameResultId() {
        String photoId = storePhoto(TestImages.jpeg(Color.GREEN));
        String eventId = UUID.randomUUID().toString();

        sendReportCreated(eventId, 7L, photoId);
        sendReportCreated(eventId, 7L, photoId);

        Message first = rabbit.receive(RabbitConfig.RESULT_QUEUE, TIMEOUT_MS);
        Message second = rabbit.receive(RabbitConfig.RESULT_QUEUE, TIMEOUT_MS);
        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
        assertThat(second.getMessageProperties().getMessageId())
                .isEqualTo(first.getMessageProperties().getMessageId());
    }

    @Test
    void missingPhotoGoesToDeadLetterQueue() {
        String eventId = UUID.randomUUID().toString();

        sendReportCreated(eventId, 99L, new ObjectId().toHexString());

        Message dead = rabbit.receive(RabbitConfig.CLASSIFICATION_DLQ, TIMEOUT_MS);
        assertThat(dead).isNotNull();
        assertThat(dead.getMessageProperties().getMessageId()).isEqualTo(eventId);
        assertThat(rabbit.receive(RabbitConfig.RESULT_QUEUE, 500)).isNull();
    }

    @Test
    void unreadablePhotoGoesToDeadLetterQueue() {
        String photoId = storePhoto("não é uma foto".getBytes(StandardCharsets.UTF_8));
        String eventId = UUID.randomUUID().toString();

        sendReportCreated(eventId, 100L, photoId);

        Message dead = rabbit.receive(RabbitConfig.CLASSIFICATION_DLQ, TIMEOUT_MS);
        assertThat(dead).isNotNull();
        assertThat(dead.getMessageProperties().getMessageId()).isEqualTo(eventId);
    }

    private String storePhoto(byte[] content) {
        return gridFs.store(new ByteArrayInputStream(content), UUID.randomUUID().toString(), "image/jpeg")
                .toHexString();
    }

    // Mesmo formato publicado pela outbox da API
    private void sendReportCreated(String eventId, Long reportId, String photoId) {
        String payload = """
                {"reportId": %d, "issueId": 1, "category": "POTHOLE", "photoId": "%s"}
                """.formatted(reportId, photoId);
        Message message = MessageBuilder.withBody(payload.getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setMessageId(eventId)
                .setType(RabbitConfig.REPORT_CREATED)
                .build();
        rabbit.send(RabbitConfig.EVENTS_EXCHANGE, RabbitConfig.REPORT_CREATED, message);
    }
}
