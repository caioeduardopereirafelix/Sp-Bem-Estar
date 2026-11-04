package com.br.spbemestar.classifier.dto;

public record ReportClassified(Long reportId, String predictedCategory, double confidence, String modelVersion) {
}
