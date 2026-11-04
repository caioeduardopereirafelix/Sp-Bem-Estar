package com.br.spbemestar.dto;

public record ReportClassifiedMessage(Long reportId, String predictedCategory, Double confidence,
                                      String modelVersion) {
}
