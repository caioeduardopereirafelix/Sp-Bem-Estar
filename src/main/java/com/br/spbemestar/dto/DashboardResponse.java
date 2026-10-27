package com.br.spbemestar.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * O painel do administrador: o produto (do banco), a saúde de cada parte e o que aconteceu desde que a API subiu
 * (da memória do processo, que zera a cada reinício).
 */
public record DashboardResponse(Instant generatedAt, Product product, SystemHealth health, Activity activity,
                                List<RecentError> recentErrors) {

    /**
     * @param reportsByDay            relatos recebidos por dia, nos últimos 14 dias (horário de São Paulo)
     * @param reporters30d            pessoas diferentes que relataram nos últimos 30 dias (contagem, sem identificar)
     * @param openByStatus            chamados em aberto por status ({@code OPEN}, {@code CONFIRMED}, {@code IN_PROGRESS})
     * @param medianResolutionHours30d mediana do primeiro relato até a resolução, nos chamados resolvidos em 30 dias
     * @param photoChecks30d          o que o classificador achou das fotos dos relatos de 30 dias, por resultado
     */
    public record Product(long reportsToday, long reports7d, long reports30d, long reportsTotal,
                          List<DailyCount> reportsByDay, long reporters30d, Map<String, Long> openByStatus,
                          long resolved30d, long resolvedTotal, Double medianResolutionHours30d,
                          List<Ranked> topCategories, List<Ranked> topNeighborhoods, long moderationPending,
                          long rejected30d, Map<String, Long> photoChecks30d, long pushSubscriptions) {
    }

    public record DailyCount(LocalDate day, long count) {
    }

    public record Ranked(String code, String name, long count) {
    }

    /**
     * @param status     o status geral do Actuator ({@code UP}, {@code DOWN}, {@code OUT_OF_SERVICE}...): o pior entre
     *                   as partes da API, sem contar o worker
     * @param components banco, fotos, filas, outbox e disco, com o status de cada um
     */
    public record SystemHealth(String status, List<Component> components, Worker worker, Queues queues,
                               Runtime runtime) {
    }

    public record Component(String name, String label, String status) {
    }

    /**
     * @param status      status do worker ({@code UP}, {@code DOWN}), {@code UNREACHABLE} se ele não respondeu ou
     *                    {@code NOT_CONFIGURED} sem {@code CLASSIFIER_URL}
     * @param classifier  status do modelo de classificação dentro do worker
     */
    public record Worker(String status, String classifier) {
    }

    public record Queues(long outboxPending, long outboxOldestSeconds, Map<String, Long> deadLetters) {
    }

    public record Runtime(Instant startedAt, long uptimeSeconds, long heapUsedMb, long heapMaxMb, Double cpuUsage) {
    }

    /**
     * Contadores desde que a API subiu.
     *
     * @param precheck conferência da foto na hora do envio, por resultado ({@code passed}, {@code rejected},
     *                 {@code insisted}, {@code unavailable})
     */
    public record Activity(long httpRequests, long http4xx, long http5xx, Double httpMeanMs, long loginFailures,
                           long rateLimited, Map<String, Long> precheck) {
    }

    public record RecentError(Instant at, String method, String path, String error, String message, String traceId) {
    }
}
