package com.br.spbemestar.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record DashboardResponse(Instant generatedAt, Product product, SystemHealth health, Activity activity,
                                List<RecentError> recentErrors) {

    public record Product(long reportsToday, long reports7d, long reports30d, long reportsTotal,
                          List<DailyCount> reportsByDay, long reporters30d, Map<String, Long> openByStatus,
                          long resolved30d, long resolvedTotal, Double medianResolutionHours30d,
                          List<Ranked> topCategories, List<Ranked> topNeighborhoods, long moderationPending,
                          long rejected30d, Map<String, Long> photoChecks30d, long pushSubscriptions,
                          long citizenAccounts) {
    }

    public record DailyCount(LocalDate day, long count) {
    }

    public record Ranked(String code, String name, long count) {
    }

    public record SystemHealth(String status, List<Component> components, Worker worker, Queues queues,
                               Runtime runtime) {
    }

    public record Component(String name, String label, String status) {
    }

    public record Worker(String status, String classifier) {
    }

    public record Queues(long outboxPending, long outboxOldestSeconds, Map<String, Long> deadLetters) {
    }

    public record Runtime(Instant startedAt, long uptimeSeconds, long heapUsedMb, long heapMaxMb, Double cpuUsage) {
    }

    public record Activity(long httpRequests, long http4xx, long http5xx, Double httpMeanMs, long loginFailures,
                           long rateLimited, Map<String, Long> precheck) {
    }

    public record RecentError(Instant at, String method, String path, String error, String message, String traceId) {
    }
}
