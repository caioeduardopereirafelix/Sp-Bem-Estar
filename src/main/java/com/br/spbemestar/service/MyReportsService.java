package com.br.spbemestar.service;

import com.br.spbemestar.dto.IssueDetailResponse;
import com.br.spbemestar.dto.MyReportItem;
import com.br.spbemestar.dto.PageResponse;
import com.br.spbemestar.model.Issue;
import com.br.spbemestar.model.IssueStatusHistory;
import com.br.spbemestar.model.ModerationDecision;
import com.br.spbemestar.model.Report;
import com.br.spbemestar.repository.IssueStatusHistoryRepository;
import com.br.spbemestar.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** O que o cidadão vê dos próprios relatos: em que chamado entraram e como o atendimento andou. */
@Service
@RequiredArgsConstructor
public class MyReportsService {

    private final ReportRepository reports;
    private final IssueStatusHistoryRepository history;
    private final ReporterHasher hasher;

    @Transactional(readOnly = true)
    public PageResponse<MyReportItem> list(UUID reporterId, int page, int size) {
        Page<Report> result = reports.findByReporter(hasher.reporterHash(reporterId), PageRequest.of(page, size));
        List<Long> issueIds = result.getContent().stream().map(r -> r.getIssue().getId()).distinct().toList();
        // sem o nome do operador: o cidadão vê o que mudou e quando, não quem mudou
        Map<Long, List<IssueDetailResponse.StatusChange>> changes = issueIds.isEmpty() ? Map.of()
                : history.findByIssueIdInOrderByChangedAt(issueIds).stream().collect(Collectors.groupingBy(
                        h -> h.getIssue().getId(), Collectors.mapping(MyReportsService::change, Collectors.toList())));
        return PageResponse.of(result, r -> item(r, changes.getOrDefault(r.getIssue().getId(), List.of())));
    }

    private static MyReportItem item(Report report, List<IssueDetailResponse.StatusChange> changes) {
        Issue issue = report.getIssue();
        return new MyReportItem(
                report.getId(),
                issue.getId(),
                report.getCategory().getCode(),
                report.getCategory().getName(),
                issue.getStatus().name(),
                report.getModerationStatus() == ModerationDecision.REJECTED,
                report.getCreatedAt(),
                changes.isEmpty() ? report.getCreatedAt() : changes.getLast().changedAt(),
                issue.getLocation().getY(),
                issue.getLocation().getX(),
                changes,
                IssueDetailResponse.Resolution.of(issue.getResolutionPhotoId(), issue.getResolutionNote()));
    }

    private static IssueDetailResponse.StatusChange change(IssueStatusHistory h) {
        return new IssueDetailResponse.StatusChange(h.getFromStatus() == null ? null : h.getFromStatus().name(),
                h.getToStatus().name(), h.getChangedAt(), null);
    }
}
