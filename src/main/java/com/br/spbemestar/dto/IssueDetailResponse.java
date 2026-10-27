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
        List<StatusChange> history,
        Resolution resolution
) {

    /**
     * O que a equipe mostrou ao resolver; vazio se o chamado não foi resolvido ou se ninguém deixou foto nem nota.
     */
    public record Resolution(String photoUrl, String note) {

        public static Resolution of(String photoId, String note) {
            return photoId == null && note == null ? null
                    : new Resolution(photoId == null ? null : "/api/v1/photos/" + photoId, note);
        }
    }

    public record ReportItem(Long id, Instant createdAt, String photoUrl, String classificationStatus) {
    }

    /**
     * @param changedBy nome do operador que fez a mudança; só preenchido para quem está logado como operador
     */
    public record StatusChange(String from, String to, Instant changedAt, String changedBy) {
    }
}
