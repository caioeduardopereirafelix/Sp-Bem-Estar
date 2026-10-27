package com.br.spbemestar.dto;

import java.time.Instant;
import java.util.List;

/**
 * Um relato do próprio cidadão e o andamento do chamado em que ele entrou.
 *
 * @param rejected    a moderação recusou o relato; ele não conta mais no chamado
 * @param updatedAt   última mudança de status do chamado, ou o envio, se ainda não houve nenhuma
 * @param resolution  a foto do conserto e a nota da equipe, quando o chamado foi resolvido com elas
 * @param photoCheck  o que o classificador achou da foto; vazio depois que a moderação decidiu
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
        List<IssueDetailResponse.StatusChange> history,
        IssueDetailResponse.Resolution resolution,
        PhotoCheck photoCheck
) {

    /**
     * @param status            {@code PENDING} enquanto o classificador não respondeu; {@code AGREED}, {@code MISMATCH}
     *                          ou {@code LOW_CONFIDENCE} depois
     * @param predictedCategory o tipo que o modelo viu na foto ({@code OTHER}: nenhum problema de rua)
     */
    public record PhotoCheck(String status, String predictedCategory) {
    }
}
