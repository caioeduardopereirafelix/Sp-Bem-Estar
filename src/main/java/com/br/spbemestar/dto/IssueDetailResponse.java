package com.br.spbemestar.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record IssueDetailResponse(
        Long id,
        String category,
        String categoryName,
        String status,
        double latitude,
        double longitude,
        String neighborhood,
        int reportCount,
        int reporterCount,
        BigDecimal priorityScore,
        Instant firstReportedAt,
        Instant lastReportedAt,
        Instant resolvedAt,
        List<ReportItem> reports,
        List<StatusChange> history
) {

    public record ReportItem(Long id, Instant createdAt, String photoUrl, String classificationStatus) {
    }

    /**
     * @param changedBy nome do operador que fez a mudança; só preenchido para quem está logado como operador
     */
    public record StatusChange(String from, String to, Instant changedAt, String changedBy) {
    }
}
