package com.br.spbemestar.classifier.dto;

public record ClassifyResponse(String label, double confidence, String modelVersion) {
}
