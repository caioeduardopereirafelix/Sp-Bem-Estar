package com.br.spbemestar.dto;

import com.br.spbemestar.service.ReportService;


public record ReportResponse(Long reportId, Long issueId, boolean newIssue, int issueReportCount,
                             String issueStatus) {

    public static ReportResponse from(ReportService.ReportResult result) {
        return new ReportResponse(result.reportId(), result.issueId(), result.newIssue(),
                result.issueReportCount(), result.issueStatus());
    }
}
