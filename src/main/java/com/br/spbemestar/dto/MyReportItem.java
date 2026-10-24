package com.br.spbemestar.dto;

import java.time.Instant;
import java.util.List;

/**
 * Um relato do próprio cidadão e o andamento do chamado em que ele entrou.
 *
 * @param rejected    a moderação recusou o relato; ele não conta mais no chamado
 * @param updatedAt   última mudança de status do chamado, ou o envio, se ainda não houve nenhuma
 */
public record MyReportItem(
        Long reportId,
        Long issueId,
        String category,
        String categoryName,
        String issueStatus,
        boolean rejected,
        Instant sentAt,
        Instant updatedAt,
        double latitude,
        double longitude,
        List<IssueDetailResponse.StatusChange> history
) {
}
