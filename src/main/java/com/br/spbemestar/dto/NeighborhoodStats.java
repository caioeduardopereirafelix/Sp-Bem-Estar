package com.br.spbemestar.dto;

import java.math.BigDecimal;

public record NeighborhoodStats(
        Long id,
        String name,
        String city,
        Integer population,
        int openIssues,
        int resolvedIssues,
        int openReports,
        BigDecimal openIssuesPer10k,
        BigDecimal averagePriority
) {
}
