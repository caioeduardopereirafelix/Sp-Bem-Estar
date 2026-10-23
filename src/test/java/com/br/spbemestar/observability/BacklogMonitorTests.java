package com.br.spbemestar.observability;

import com.br.spbemestar.TestPhotos;
import com.br.spbemestar.TestReporters;
import com.br.spbemestar.TestcontainersConfiguration;
import com.br.spbemestar.config.RabbitConfig;
import com.br.spbemestar.service.ReportService;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

// Publicador parado (mesmo contexto do OutboxPublisherTests): o evento antigo do teste fica pendente
@SpringBootTest(properties = "app.outbox.publish-interval-ms=3600000")
@Import(TestcontainersConfiguration.class)
class BacklogMonitorTests {

    @Autowired BacklogMonitor monitor;
    @Autowired MeterRegistry meters;
    @Autowired JdbcTemplate jdbc;
    @Autowired RabbitTemplate rabbit;
    @Autowired AmqpAdmin admin;
    @Autowired ReportService reportService;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM outbox_event WHERE aggregate_id = -42");
        admin.purgeQueue(RabbitConfig.CLASSIFICATION_DLQ, false);
        monitor.refresh();
    }

    @Test
    void stuckOutboxDegradesTheHealthCheck() {
        monitor.refresh();
        assertThat(monitor.health().getStatus()).isEqualTo(Status.UP);

        jdbc.update("""
                INSERT INTO outbox_event (id, aggregate_id, type, payload, created_at)
                VALUES (gen_random_uuid(), -42, 'ReportCreated', '{}', now() - interval '10 minutes')
                """);
        monitor.refresh();

        assertThat(gauge("spbemestar.outbox.pending")).isGreaterThanOrEqualTo(1);
        assertThat(gauge("spbemestar.outbox.oldest.age")).isGreaterThanOrEqualTo(600);
        // a API continua atendendo, mas os relatos não chegam ao classificador
        assertThat(monitor.health().getStatus()).isEqualTo(BacklogMonitor.DEGRADED);
    }

    @Test
    void countsMessagesStuckInTheDeadLetterQueue() {
        admin.purgeQueue(RabbitConfig.CLASSIFICATION_DLQ, false);
        rabbit.convertAndSend("", RabbitConfig.CLASSIFICATION_DLQ, "mensagem que ninguém conseguiu processar");

        monitor.refresh();

        assertThat(meters.get("spbemestar.rabbitmq.dead.letters").tag("queue", RabbitConfig.CLASSIFICATION_DLQ)
                .gauge().value()).isEqualTo(1);
    }

    @Test
    void countsOpenIssuesAndPendingModeration() {
        reportService.create(TestReporters.ID, "POTHOLE", -29.80, -49.80, TestPhotos.jpeg());
        monitor.refresh();
        double open = meters.get("spbemestar.issues.open").tag("status", "OPEN").gauge().value();

        assertThat(open).isPositive();
        assertThat(open).isEqualTo(jdbc.queryForObject("SELECT count(*) FROM issue WHERE status = 'OPEN'", Double.class));
        assertThat(gauge("spbemestar.moderation.pending")).isEqualTo(jdbc.queryForObject("""
                SELECT count(*) FROM report
                WHERE classification_status IN ('MISMATCH', 'LOW_CONFIDENCE') AND moderation_status IS NULL
                """, Double.class));
    }

    private double gauge(String name) {
        return meters.get(name).gauge().value();
    }
}
