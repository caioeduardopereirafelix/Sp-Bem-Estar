package com.br.spbemestar.dto;

import com.br.spbemestar.model.IssueStatusHistory;
import com.br.spbemestar.util.PhotoUrls;

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

    public record Resolution(String photoUrl, String note) {

        public static Resolution of(String photoId, String note) {
            return photoId == null && note == null ? null
                    : new Resolution(PhotoUrls.path(photoId), note);
        }
    }

    public record ReportItem(Long id, Instant createdAt, String photoUrl, String classificationStatus) {
    }

    public record StatusChange(String from, String to, Instant changedAt, String changedBy) {

        public static StatusChange of(IssueStatusHistory change, String changedBy) {
            return new StatusChange(change.getFromStatus() == null ? null : change.getFromStatus().name(),
                    change.getToStatus().name(), change.getChangedAt(), changedBy);
        }
    }
}
