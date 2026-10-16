package com.br.spbemestar.service;

import com.br.spbemestar.repository.IssueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class PriorityService {

    private final IssueRepository issues;
    private final Clock clock;

    @Transactional
    public void recalculate(Long issueId) {
        issues.recalculatePriority(issueId, Instant.now(clock));
    }

    // A idade do chamado entra na fórmula, então a prioridade sobe mesmo sem relatos novos
    @Scheduled(cron = "${app.priority.recalculate-cron:0 0 * * * *}")
    @Transactional
    public int recalculateOpenIssues() {
        int updated = issues.recalculateOpenPriorities(Instant.now(clock));
        log.info("Prioridade recalculada para {} chamados abertos", updated);
        return updated;
    }
}
