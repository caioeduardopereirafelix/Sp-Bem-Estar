package com.br.spbemestar.observability;

import com.br.spbemestar.config.RabbitConfig;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tamanho das filas de trabalho, medido a cada 30 s: chamados em aberto por status, relatos esperando
 * moderação, eventos parados na outbox e mensagens nas DLQs. Os valores ficam em memória, então um
 * scrape do Prometheus não consulta o banco.
 * <p>
 * Também é o health check {@code outbox}: se o evento pendente mais antigo passa do limite, o status vira
 * {@code DEGRADED} (a API continua atendendo, mas os relatos não chegam ao classificador).
 */
@Slf4j
@Component("outbox")
public class BacklogMonitor implements HealthIndicator {

    public static final Status DEGRADED = new Status("DEGRADED");

    private static final String COUNTS = """
            SELECT
                (SELECT count(*) FROM issue WHERE status = 'OPEN') AS open,
                (SELECT count(*) FROM issue WHERE status = 'CONFIRMED') AS confirmed,
                (SELECT count(*) FROM issue WHERE status = 'IN_PROGRESS') AS in_progress,
                (SELECT count(*) FROM report
                 WHERE classification_status IN ('MISMATCH', 'LOW_CONFIDENCE') AND moderation_status IS NULL)
                    AS moderation_pending,
                (SELECT count(*) FROM outbox_event WHERE published_at IS NULL) AS outbox_pending,
                (SELECT coalesce(EXTRACT(EPOCH FROM now() - min(created_at)), 0)::bigint
                 FROM outbox_event WHERE published_at IS NULL) AS outbox_oldest_seconds
            """;

    private final JdbcTemplate jdbc;
    private final AmqpAdmin amqp;
    private final Duration outboxLagLimit;

    private final Map<String, AtomicLong> issuesByStatus = Map.of(
            "OPEN", new AtomicLong(), "CONFIRMED", new AtomicLong(), "IN_PROGRESS", new AtomicLong());
    private final AtomicLong moderationPending = new AtomicLong();
    private final AtomicLong outboxPending = new AtomicLong();
    private final AtomicLong outboxOldestSeconds = new AtomicLong();
    private final Map<String, AtomicLong> deadLetters = Map.of(
            RabbitConfig.CLASSIFICATION_DLQ, new AtomicLong(), RabbitConfig.RESULT_DLQ, new AtomicLong());

    public BacklogMonitor(JdbcTemplate jdbc, AmqpAdmin amqp, MeterRegistry meters,
                          @Value("${app.observability.outbox-lag-limit:5m}") Duration outboxLagLimit) {
        this.jdbc = jdbc;
        this.amqp = amqp;
        this.outboxLagLimit = outboxLagLimit;

        issuesByStatus.forEach((status, value) -> Gauge.builder("spbemestar.issues.open", value, AtomicLong::get)
                .description("Chamados em aberto, por status").tag("status", status).register(meters));
        Gauge.builder("spbemestar.moderation.pending", moderationPending, AtomicLong::get)
                .description("Relatos marcados pelo classificador esperando moderação").register(meters);
        Gauge.builder("spbemestar.outbox.pending", outboxPending, AtomicLong::get)
                .description("Eventos da outbox ainda não publicados no RabbitMQ").register(meters);
        Gauge.builder("spbemestar.outbox.oldest.age", outboxOldestSeconds, AtomicLong::get)
                .description("Idade do evento pendente mais antigo da outbox").baseUnit("seconds").register(meters);
        deadLetters.forEach((queue, value) -> Gauge.builder("spbemestar.rabbitmq.dead.letters", value, AtomicLong::get)
                .description("Mensagens paradas numa DLQ").tag("queue", queue).register(meters));
    }

    @Scheduled(fixedDelayString = "${app.observability.backlog-interval:30s}", initialDelay = 0)
    public void refresh() {
        try {
            jdbc.query(COUNTS, rs -> {
                issuesByStatus.get("OPEN").set(rs.getLong("open"));
                issuesByStatus.get("CONFIRMED").set(rs.getLong("confirmed"));
                issuesByStatus.get("IN_PROGRESS").set(rs.getLong("in_progress"));
                moderationPending.set(rs.getLong("moderation_pending"));
                outboxPending.set(rs.getLong("outbox_pending"));
                outboxOldestSeconds.set(rs.getLong("outbox_oldest_seconds"));
            });
            deadLetters.forEach((queue, value) -> {
                QueueInformation info = amqp.getQueueInfo(queue);
                value.set(info == null ? 0 : info.getMessageCount());
            });
        } catch (RuntimeException e) {
            // banco ou broker fora do ar: os health checks deles já acusam; mantém os últimos valores
            log.warn("Não foi possível medir as filas de trabalho: {}", e.getMessage());
        }
    }

    @Override
    public Health health() {
        long oldest = outboxOldestSeconds.get();
        Health.Builder health = oldest > outboxLagLimit.toSeconds() ? Health.status(DEGRADED) : Health.up();
        return health.withDetail("pending", outboxPending.get())
                .withDetail("oldestPendingSeconds", oldest)
                .build();
    }
}
