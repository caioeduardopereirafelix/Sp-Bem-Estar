package com.br.spbemestar.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record IssueSummary(
        Long id,
        String categoryCode,
        String categoryName,
        String status,
        int reportCount,
        int reporterCount,
        BigDecimal priorityScore,
        Instant firstReportedAt,
        Instant lastReportedAt,
        String neighborhoodName,
        double latitude,
        double longitude
) {
}
