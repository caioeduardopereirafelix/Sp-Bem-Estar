package com.br.spbemestar.dto;

public record ModerationResponse(Long reportId, String decision, Long issueId, String issueStatus,
                                 int issueReportCount, boolean reporterBlocked) {
}
