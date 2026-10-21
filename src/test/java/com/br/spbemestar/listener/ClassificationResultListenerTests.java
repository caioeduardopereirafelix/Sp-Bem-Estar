package com.br.spbemestar.listener;

import com.br.spbemestar.TestPhotos;
import com.br.spbemestar.TestReporters;
import com.br.spbemestar.TestcontainersConfiguration;
import com.br.spbemestar.config.RabbitConfig;
import com.br.spbemestar.service.ReportService;
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
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ClassificationResultListenerTests {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Autowired ReportService reportService;
    @Autowired RabbitTemplate rabbit;
    @Autowired AmqpAdmin admin;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void purge() {
        admin.purgeQueue(RabbitConfig.RESULT_DLQ, false);
    }

    @Test
    void agreementKeepsTheReportOutOfModeration() {
        Long reportId = report("POTHOLE", -23.00);

        sendResult(reportId, "POTHOLE", 0.9876);

        awaitStatus(reportId, "AGREED");
        Map<String, Object> row = classification(reportId);
        assertThat(row.get("predicted_category")).isEqualTo("POTHOLE");
        assertThat(row.get("confidence")).isEqualTo(new BigDecimal("0.988"));
    }

    @Test
    void divergentCategoryIsFlagged() {
        Long reportId = report("POTHOLE", -23.01);

        sendResult(reportId, "TRASH", 0.95);

        awaitStatus(reportId, "MISMATCH");
    }

    @Test
    void lowConfidenceIsFlaggedEvenWhenTheCategoryMatches() {
        Long reportId = report("TRASH", -23.02);

        sendResult(reportId, "TRASH", 0.41);

        awaitStatus(reportId, "LOW_CONFIDENCE");
    }

    @Test
    void onlyTheFirstResultCounts() {
        Long reportId = report("POTHOLE", -23.03);
        sendResult(reportId, "POTHOLE", 0.9);
        awaitStatus(reportId, "AGREED");

        sendResult(reportId, "TRASH", 0.99);
        // a fila tem um consumidor só: quando o marcador for processado, a mensagem repetida também foi
        Long marker = report("POTHOLE", -23.04);
        sendResult(marker, "POTHOLE", 0.9);
        awaitStatus(marker, "AGREED");

        assertThat(classification(reportId))
                .containsEntry("classification_status", "AGREED")
                .containsEntry("predicted_category", "POTHOLE");
    }

    @Test
    void unknownReportGoesToDeadLetterQueue() {
        sendResult(Long.MAX_VALUE, "POTHOLE", 0.9);

        assertThat(rabbit.receive(RabbitConfig.RESULT_DLQ, TIMEOUT.toMillis())).isNotNull();
    }

    @Test
    void malformedResultGoesToDeadLetterQueue() {
        send("""
                {"reportId": 1, "predictedCategory": "POTHOLE", "confidence": 1.7}
                """);

        assertThat(rabbit.receive(RabbitConfig.RESULT_DLQ, TIMEOUT.toMillis())).isNotNull();
    }

    private Long report(String category, double lat) {
        return reportService.create(TestReporters.ID, category, lat, -47.00, TestPhotos.jpeg()).reportId();
    }

    private void sendResult(Long reportId, String predicted, double confidence) {
        send("""
                {"reportId": %d, "predictedCategory": "%s", "confidence": %s, "modelVersion": "test"}
                """.formatted(reportId, predicted, confidence));
    }

    // Mesmo formato publicado pelo classifier-worker
    private void send(String payload) {
        Message message = MessageBuilder.withBody(payload.getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setMessageId(UUID.randomUUID().toString())
                .setType(RabbitConfig.REPORT_CLASSIFIED)
                .build();
        rabbit.send(RabbitConfig.EVENTS_EXCHANGE, RabbitConfig.REPORT_CLASSIFIED, message);
    }

    private void awaitStatus(Long reportId, String status) {
        await().atMost(TIMEOUT).untilAsserted(() ->
                assertThat(classification(reportId)).containsEntry("classification_status", status));
    }

    private Map<String, Object> classification(Long reportId) {
        return jdbc.queryForMap(
                "SELECT classification_status, predicted_category, confidence FROM report WHERE id = ?", reportId);
    }
}
