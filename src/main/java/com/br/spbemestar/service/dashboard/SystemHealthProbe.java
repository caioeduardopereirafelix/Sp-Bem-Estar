package com.br.spbemestar.service.dashboard;

import com.br.spbemestar.config.PhotoPrecheckProperties;
import com.br.spbemestar.config.RestClients;
import com.br.spbemestar.dto.DashboardResponse;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.health.actuate.endpoint.CompositeHealthDescriptor;
import org.springframework.boot.health.actuate.endpoint.HealthDescriptor;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Component
public class SystemHealthProbe {

    private static final Map<String, String> COMPONENTS = linked(
            "db", "Banco de dados (PostgreSQL)",
            "mongo", "Fotos (MongoDB)",
            "rabbit", "Filas (RabbitMQ)",
            "outbox", "Envio para o classificador (outbox)",
            "diskSpace", "Disco do servidor",
            "cache", "Cache (Redis)");

    private final MeterRegistry meters;
    private final ObjectProvider<HealthEndpoint> healthEndpoint;
    private final PhotoPrecheckProperties worker;
    private final RestClient workerClient;
    private final Clock clock;

    public SystemHealthProbe(MeterRegistry meters, ObjectProvider<HealthEndpoint> healthEndpoint,
                             PhotoPrecheckProperties worker, Clock clock) {
        this.meters = meters;
        this.healthEndpoint = healthEndpoint;
        this.worker = worker;
        this.clock = clock;
        this.workerClient = RestClients.withTimeout(Duration.ofSeconds(2)).build();
    }

    public DashboardResponse.SystemHealth read() {
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

    private double gauge(String name) {
        Gauge gauge = meters.find(name).gauge();
        return gauge == null ? 0 : gauge.value();
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
