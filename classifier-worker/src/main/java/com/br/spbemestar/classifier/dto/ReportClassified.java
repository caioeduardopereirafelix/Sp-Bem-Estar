package com.br.spbemestar.classifier.dto;

/**
 * Resultado da classificação, consumido pela API.
 */
public record ReportClassified(Long reportId, String predictedCategory, double confidence, String modelVersion) {
}
