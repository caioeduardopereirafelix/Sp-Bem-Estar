package com.br.spbemestar.service;

import com.br.spbemestar.dto.PageResponse;
import com.br.spbemestar.dto.QueueItem;
import com.br.spbemestar.model.ClassificationStatus;
import com.br.spbemestar.model.Issue;
import com.br.spbemestar.model.IssueStatus;
import com.br.spbemestar.repository.IssueRepository;
import com.br.spbemestar.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Fila de atendimento do órgão público: chamados em aberto, do mais urgente para o menos. */
@Service
@RequiredArgsConstructor
public class OperatorQueueService {

    private static final Set<IssueStatus> ACTIVE = EnumSet.of(IssueStatus.OPEN, IssueStatus.CONFIRMED,
            IssueStatus.IN_PROGRESS);

    private final IssueRepository issues;
    private final ReportRepository reports;

    /**
     * @param status sem filtro, os três status em andamento; com filtro, inclusive resolvidos e descartados
     */
    @Transactional(readOnly = true)
    public PageResponse<QueueItem> queue(IssueStatus status, String category, Long neighborhoodId, int page, int size) {
        Set<IssueStatus> statuses = status == null ? ACTIVE : EnumSet.of(status);
        Page<Issue> result = issues.findQueue(statuses, category, neighborhoodId, PageRequest.of(page, size));

        List<Long> ids = result.getContent().stream().map(Issue::getId).toList();
        Map<Long, Long> pending = ids.isEmpty() ? Map.of()
                : reports.countPendingModerationByIssue(ids, ClassificationStatus.NEEDS_REVIEW).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));

        return PageResponse.of(result, issue -> new QueueItem(
                issue.getId(),
                issue.getCategory().getCode(),
                issue.getCategory().getName(),
                issue.getStatus().name(),
                issue.getNeighborhood() == null ? null : issue.getNeighborhood().getName(),
                issue.getReportCount(),
                pending.getOrDefault(issue.getId(), 0L),
                issue.getPriorityScore(),
                issue.getFirstReportedAt(),
                issue.getLastReportedAt(),
                issue.getLocation().getY(),
                issue.getLocation().getX()));
    }
}
