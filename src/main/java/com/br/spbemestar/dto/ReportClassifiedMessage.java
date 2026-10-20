package com.br.spbemestar.dto;

/**
 * Resultado publicado pelo classifier-worker.
 */
public record ReportClassifiedMessage(Long reportId, String predictedCategory, Double confidence,
                                      String modelVersion) {
}
