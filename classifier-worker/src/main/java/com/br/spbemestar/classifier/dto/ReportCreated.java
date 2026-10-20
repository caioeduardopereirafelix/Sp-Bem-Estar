package com.br.spbemestar.classifier.dto;

/**
 * Evento publicado pela API quando um relato é criado (payload da outbox).
 */
public record ReportCreated(Long reportId, Long issueId, String category, String photoId) {
}
