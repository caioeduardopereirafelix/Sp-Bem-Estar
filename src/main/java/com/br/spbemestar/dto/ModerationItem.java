package com.br.spbemestar.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** Relato marcado pelo classificador, esperando a decisão de um operador. */
public record ModerationItem(
        Long reportId,
        Long issueId,
        String issueStatus,
        String category,
        String categoryName,
        String predictedCategory,
        String predictedCategoryName,
        BigDecimal confidence,
        String classificationStatus,
        String photoUrl,
        Instant createdAt,
        boolean reporterBlocked
) {
}
