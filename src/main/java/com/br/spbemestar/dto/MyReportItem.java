package com.br.spbemestar.dto;

import java.time.Instant;
import java.util.List;

public record MyReportItem(
        Long reportId,
        Long issueId,
        String category,
        String categoryName,
        String issueStatus,
        boolean rejected,
        Instant sentAt,
        Instant updatedAt,
        double latitude,
        double longitude,
        List<IssueDetailResponse.StatusChange> history,
        IssueDetailResponse.Resolution resolution,
        PhotoCheck photoCheck
) {

    public record PhotoCheck(String status, String predictedCategory) {
    }
}
