package com.br.spbemestar.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Chamado na fila de atendimento.
 *
 * @param pendingModeration relatos deste chamado que o classificador marcou e ainda esperam moderação
 */
public record QueueItem(
        Long id,
        String category,
        String categoryName,
        String status,
        String neighborhood,
        int reportCount,
        long pendingModeration,
        BigDecimal priorityScore,
        Instant firstReportedAt,
        Instant lastReportedAt,
        double latitude,
        double longitude
) {
}
