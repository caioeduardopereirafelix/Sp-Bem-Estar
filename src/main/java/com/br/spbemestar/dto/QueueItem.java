package com.br.spbemestar.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record QueueItem(
        Long id,
        String category,
        String categoryName,
        String status,
        String neighborhood,
        int reportCount,
        int reporterCount,
        long pendingModeration,
        BigDecimal priorityScore,
        Instant firstReportedAt,
        Instant lastReportedAt,
        double latitude,
        double longitude
) {
}
