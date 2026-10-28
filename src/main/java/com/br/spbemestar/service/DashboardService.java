package com.br.spbemestar.service;

import com.br.spbemestar.config.PhotoPrecheckProperties;
import com.br.spbemestar.dto.DashboardResponse;
import com.br.spbemestar.observability.RecentErrors;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.health.actuate.endpoint.CompositeHealthDescriptor;
import org.springframework.boot.health.actuate.endpoint.HealthDescriptor;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

/**
 * Junta o painel do administrador. Os números do produto vêm do banco; a saúde, dos mesmos health checks do
 * Actuator; a atividade, dos contadores do Micrometer, que contam desde que a API subiu.
 */
@Service
public class DashboardService {

    /** As partes que importam para quem administra; os outros health checks (ping, ssl) só poluiriam. */
    private static final Map<String, String> COMPONENTS = linked(
            "db", "Banco de dados (PostgreSQL)",
            "mongo", "Fotos (MongoDB)",
            "rabbit", "Filas (RabbitMQ)",
            "outbox", "Envio para o classificador (outbox)",
            "diskSpace", "Disco do servidor");

    private static final String PRODUCT = """
            SELECT
                (SELECT count(*) FROM report
                 WHERE created_at >= (date_trunc('day', now() AT TIME ZONE 'America/Sao_Paulo')
                                      AT TIME ZONE 'America/Sao_Paulo')) AS reports_today,
                (SELECT count(*) FROM report WHERE created_at > now() - interval '7 days') AS reports_7d,
                (SELECT count(*) FROM report WHERE created_at > now() - interval '30 days') AS reports_30d,
                (SELECT count(*) FROM report) AS reports_total,
                (SELECT count(DISTINCT reporter_hash) FROM report WHERE created_at > now() - interval '30 days')
                    AS reporters_30d,
                (SELECT count(*) FROM issue WHERE status = 'RESOLVED' AND resolved_at > now() - interval '30 days')
                    AS resolved_30d,
                (SELECT count(*) FROM issue WHERE status = 'RESOLVED') AS resolved_total,
                (SELECT percentile_cont(0.5) WITHIN GROUP (ORDER BY EXTRACT(EPOCH FROM resolved_at - first_reported_at))
                 FROM issue WHERE status = 'RESOLVED' AND resolved_at > now() - interval '30 days') / 3600
                    AS median_resolution_hours,
                (SELECT count(*) FROM report
                 WHERE classification_status IN ('MISMATCH', 'LOW_CONFIDENCE') AND moderation_status IS NULL)
                    AS moderation_pending,
                (SELECT count(*) FROM report
                 WHERE moderation_status = 'REJECTED' AND moderated_at > now() - interval '30 days') AS rejected_30d,
                (SELECT count(*) FROM push_subscription) AS push_subscriptions,
                (SELECT count(*) FROM citizen_account) AS citizen_accounts
            """;

    private final JdbcTemplate jdbc;
    private final MeterRegistry meters;
    private final ObjectProvider<HealthEndpoint> healthEndpoint;
    private final RecentErrors recentErrors;
    private final PhotoPrecheckProperties worker;
    private final RestClient workerClient;
    private final Clock clock;

    public DashboardService(JdbcTemplate jdbc, MeterRegistry meters, ObjectProvider<HealthEndpoint> healthEndpoint,
                            RecentErrors recentErrors, PhotoPrecheckProperties worker, Clock clock) {
        this.jdbc = jdbc;
        this.meters = meters;
        this.healthEndpoint = healthEndpoint;
        this.recentErrors = recentErrors;
        this.worker = worker;
        this.clock = clock;
        Duration timeout = Duration.ofSeconds(2); // o painel não pode travar esperando um worker fora do ar
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1).connectTimeout(timeout).build());
        requests.setReadTimeout(timeout);
        this.workerClient = RestClient.builder().requestFactory(requests).build();
    }

    @Transactional(readOnly = true)
    public DashboardResponse dashboard() {
        List<DashboardResponse.RecentError> errors = recentErrors.latest(20).stream()
                .map(e -> new DashboardResponse.RecentError(e.at(), e.method(), e.path(), e.error(), e.message(),
                        e.traceId()))
                .toList();
        return new DashboardResponse(Instant.now(clock), product(), health(), activity(), errors);
    }

    private DashboardResponse.Product product() {
        Map<String, Object> row = jdbc.queryForMap(PRODUCT);
        List<DashboardResponse.DailyCount> byDay = jdbc.query("""
                SELECT d::date AS day, count(r.id) AS total
                FROM generate_series((now() AT TIME ZONE 'America/Sao_Paulo')::date - 13,
                                     (now() AT TIME ZONE 'America/Sao_Paulo')::date, interval '1 day') AS d
                LEFT JOIN report r ON (r.created_at AT TIME ZONE 'America/Sao_Paulo')::date = d::date
                GROUP BY d ORDER BY d
                """, (rs, i) -> new DashboardResponse.DailyCount(rs.getDate("day").toLocalDate(), rs.getLong("total")));
        Map<String, Long> openByStatus = new LinkedHashMap<>(linkedCounts("OPEN", "CONFIRMED", "IN_PROGRESS"));
        jdbc.query("""
                SELECT status, count(*) AS total FROM issue
                WHERE status IN ('OPEN', 'CONFIRMED', 'IN_PROGRESS') GROUP BY status
                """, rs -> {
            openByStatus.put(rs.getString("status"), rs.getLong("total"));
        });
        Map<String, Long> photoChecks = new LinkedHashMap<>(
                linkedCounts("AGREED", "MISMATCH", "LOW_CONFIDENCE", "PENDING"));
        jdbc.query("""
                SELECT classification_status, count(*) AS total FROM report
                WHERE created_at > now() - interval '30 days' GROUP BY classification_status
                """, rs -> {
            photoChecks.put(rs.getString("classification_status"), rs.getLong("total"));
        });
        Number median = (Number) row.get("median_resolution_hours");
        return new DashboardResponse.Product(
                number(row, "reports_today"), number(row, "reports_7d"), number(row, "reports_30d"),
                number(row, "reports_total"), byDay, number(row, "reporters_30d"), openByStatus,
                number(row, "resolved_30d"), number(row, "resolved_total"),
                median == null ? null : Math.round(median.doubleValue() * 10) / 10.0,
                ranked("""
                        SELECT c.code, c.name, count(*) AS total FROM issue i JOIN category c ON c.code = i.category_code
                        WHERE i.status IN ('OPEN', 'CONFIRMED', 'IN_PROGRESS')
                        GROUP BY c.code, c.name ORDER BY total DESC, c.name LIMIT 5
                        """),
                ranked("""
                        SELECT n.id::text AS code, n.name, count(*) AS total FROM issue i
                        JOIN neighborhood n ON n.id = i.neighborhood_id
                        WHERE i.status IN ('OPEN', 'CONFIRMED', 'IN_PROGRESS')
                        GROUP BY n.id, n.name ORDER BY total DESC, n.name LIMIT 5
                        """),
                number(row, "moderation_pending"), number(row, "rejected_30d"), photoChecks,
                number(row, "push_subscriptions"), number(row, "citizen_accounts"));
    }

    private List<DashboardResponse.Ranked> ranked(String sql) {
        return jdbc.query(sql, (rs, i) -> new DashboardResponse.Ranked(rs.getString("code"), rs.getString("name"),
                rs.getLong("total")));
    }

    private DashboardResponse.SystemHealth health() {
        Map<String, String> statuses = new TreeMap<>();
        String overall = "UNKNOWN";
        HealthEndpoint endpoint = healthEndpoint.getIfAvailable();
        if (endpoint != null) {
            HealthDescriptor health = endpoint.health();
            overall = health.getStatus().getCode();
            if (health instanceof CompositeHealthDescriptor composite) {
                composite.getComponents().forEach((name, component) -> statuses.put(name, component.getStatus().getCode()));
            }
        }
        List<DashboardResponse.Component> components = COMPONENTS.entrySet().stream()
                .filter(c -> statuses.containsKey(c.getKey()))
                .map(c -> new DashboardResponse.Component(c.getKey(), c.getValue(), statuses.get(c.getKey())))
                .toList();

        Map<String, Long> deadLetters = new TreeMap<>();
        meters.find("spbemestar.rabbitmq.dead.letters").gauges()
                .forEach(g -> deadLetters.put(g.getId().getTag("queue"), (long) g.value()));
        DashboardResponse.Queues queues = new DashboardResponse.Queues(
                (long) gauge("spbemestar.outbox.pending"), (long) gauge("spbemestar.outbox.oldest.age"), deadLetters);

        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        long startedAt = ManagementFactory.getRuntimeMXBean().getStartTime();
        Gauge cpu = meters.find("process.cpu.usage").gauge();
        DashboardResponse.Runtime runtime = new DashboardResponse.Runtime(Instant.ofEpochMilli(startedAt),
                (clock.millis() - startedAt) / 1000, heap.getUsed() / (1024 * 1024),
                heap.getMax() < 0 ? -1 : heap.getMax() / (1024 * 1024),
                cpu == null || Double.isNaN(cpu.value()) ? null : cpu.value());
        return new DashboardResponse.SystemHealth(overall, components, workerHealth(), queues, runtime);
    }

    private DashboardResponse.Worker workerHealth() {
        if (!worker.enabled()) {
            return new DashboardResponse.Worker("NOT_CONFIGURED", null);
        }
        try {
            WorkerHealth health = workerClient.get()
                    .uri(worker.url().replaceAll("/+$", "") + "/actuator/health")
                    .retrieve()
                    // fora do ar o Actuator responde 503 com o mesmo corpo: o status vem dele, não do código HTTP
                    .onStatus(status -> status.value() == 503, (request, response) -> { })
                    .body(WorkerHealth.class);
            if (health == null) {
                return new DashboardResponse.Worker("UNREACHABLE", null);
            }
            WorkerHealth.Part classifier = health.components() == null ? null : health.components().get("classifier");
            return new DashboardResponse.Worker(health.status(), classifier == null ? null : classifier.status());
        } catch (RestClientException e) {
            return new DashboardResponse.Worker("UNREACHABLE", null);
        }
    }

    private DashboardResponse.Activity activity() {
        long requests = 0;
        long clientErrors = 0;
        long serverErrors = 0;
        double totalMs = 0;
        for (Timer timer : meters.find("http.server.requests").timers()) {
            String uri = timer.getId().getTag("uri");
            if (uri != null && uri.startsWith("/actuator")) {
                continue; // health check e scrape do Prometheus não são uso do produto
            }
            requests += timer.count();
            totalMs += timer.totalTime(TimeUnit.MILLISECONDS);
            String outcome = timer.getId().getTag("outcome");
            if ("CLIENT_ERROR".equals(outcome)) {
                clientErrors += timer.count();
            } else if ("SERVER_ERROR".equals(outcome)) {
                serverErrors += timer.count();
            }
        }
        Map<String, Long> precheck = new TreeMap<>();
        meters.find("spbemestar.reports.precheck").counters()
                .forEach(c -> precheck.merge(c.getId().getTag("outcome"), (long) c.count(), Long::sum));
        return new DashboardResponse.Activity(requests, clientErrors, serverErrors,
                requests == 0 ? null : Math.round(totalMs / requests * 10) / 10.0,
                (long) meters.find("spbemestar.auth.logins").tag("result", "failure").counters().stream()
                        .mapToDouble(Counter::count).sum(),
                (long) meters.find("spbemestar.ratelimit.rejected").counters().stream().mapToDouble(Counter::count).sum(),
                precheck);
    }

    private double gauge(String name) {
        Gauge gauge = meters.find(name).gauge();
        return gauge == null ? 0 : gauge.value();
    }

    private static long number(Map<String, Object> row, String column) {
        Object value = row.get(column);
        return value == null ? 0 : ((Number) value).longValue();
    }

    private static Map<String, Long> linkedCounts(String... keys) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String key : keys) {
            counts.put(key, 0L);
        }
        return counts;
    }

    private static Map<String, String> linked(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }

    record WorkerHealth(String status, Map<String, Part> components) {
        record Part(String status) {
        }
    }
}
