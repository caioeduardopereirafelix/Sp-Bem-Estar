package com.br.spbemestar.service;

import com.br.spbemestar.dto.BoundingBox;
import com.br.spbemestar.dto.GeoJsonFeatureCollection;
import com.br.spbemestar.dto.IssueDetailResponse;
import com.br.spbemestar.dto.PageResponse;
import com.br.spbemestar.dto.QueueItem;
import com.br.spbemestar.exception.ResourceNotFoundException;
import com.br.spbemestar.model.AppUser;
import com.br.spbemestar.model.ClassificationStatus;
import com.br.spbemestar.model.Issue;
import com.br.spbemestar.model.IssueStatus;
import com.br.spbemestar.model.IssueStatusHistory;
import com.br.spbemestar.repository.AppUserRepository;
import com.br.spbemestar.repository.IssueQueryRepository;
import com.br.spbemestar.repository.IssueRepository;
import com.br.spbemestar.repository.IssueStatusHistoryRepository;
import com.br.spbemestar.repository.ReportRepository;
import com.br.spbemestar.util.PhotoUrls;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IssueQueryService {

    private static final Set<IssueStatus> ACTIVE = EnumSet.of(IssueStatus.OPEN, IssueStatus.CONFIRMED,
            IssueStatus.IN_PROGRESS);

    private final IssueQueryRepository issueQueries;
    private final IssueRepository issues;
    private final ReportRepository reports;
    private final IssueStatusHistoryRepository history;
    private final AppUserRepository users;

    public GeoJsonFeatureCollection search(BoundingBox bbox, String category, IssueStatus status, int limit) {
        return GeoJsonFeatureCollection.of(issueQueries.search(bbox, category, status, limit));
    }

    @Transactional(readOnly = true)
    public IssueDetailResponse detail(Long id, boolean showOperators) {
        Issue issue = issues.findById(id)
                .filter(i -> i.getStatus() != IssueStatus.DISCARDED)
                .orElseThrow(() -> new ResourceNotFoundException("Chamado", id));
        List<IssueDetailResponse.ReportItem> reportItems = reports.findVisibleByIssue(id).stream()
                .map(r -> new IssueDetailResponse.ReportItem(r.getId(), r.getCreatedAt(),
                        PhotoUrls.path(r.getPhotoId()), r.getClassificationStatus().name()))
                .toList();
        List<IssueStatusHistory> changes = history.findByIssueIdOrderByChangedAt(id);
        Map<Long, String> operatorNames = showOperators ? operatorNames(changes) : Map.of();
        List<IssueDetailResponse.StatusChange> statusChanges = changes.stream()
                .map(h -> IssueDetailResponse.StatusChange.of(h,
                        h.getChangedBy() == null ? null : operatorNames.get(h.getChangedBy())))
                .toList();

        return new IssueDetailResponse(
                issue.getId(),
                issue.getCategory().getCode(),
                issue.getCategory().getName(),
                issue.getStatus().name(),
                issue.latitude(),
                issue.longitude(),
                issue.neighborhoodName(),
                issue.getReportCount(),
                issue.getReporterCount(),
                issue.getPriorityScore(),
                issue.getFirstReportedAt(),
                issue.getLastReportedAt(),
                issue.getResolvedAt(),
                reportItems,
                statusChanges,
                IssueDetailResponse.Resolution.of(issue.getResolutionPhotoId(), issue.getResolutionNote()));
    }

    @Transactional(readOnly = true)
    public PageResponse<QueueItem> queue(IssueStatus status, String category, Long neighborhoodId, int page, int size) {
        Set<IssueStatus> statuses = status == null ? ACTIVE : EnumSet.of(status);
        Page<Issue> result = issues.findQueue(statuses, category, neighborhoodId, PageRequest.of(page, size));
        Map<Long, Long> pending = pendingModeration(result.getContent().stream().map(Issue::getId).toList());

        return PageResponse.of(result, issue -> new QueueItem(
                issue.getId(),
                issue.getCategory().getCode(),
                issue.getCategory().getName(),
                issue.getStatus().name(),
                issue.neighborhoodName(),
                issue.getReportCount(),
                issue.getReporterCount(),
                pending.getOrDefault(issue.getId(), 0L),
                issue.getPriorityScore(),
                issue.getFirstReportedAt(),
                issue.getLastReportedAt(),
                issue.latitude(),
                issue.longitude()));
    }

    private Map<Long, String> operatorNames(List<IssueStatusHistory> changes) {
        List<Long> ids = changes.stream().map(IssueStatusHistory::getChangedBy).filter(Objects::nonNull)
                .distinct().toList();
        return users.findAllById(ids).stream().collect(Collectors.toMap(AppUser::getId, AppUser::getName));
    }

    private Map<Long, Long> pendingModeration(List<Long> issueIds) {
        if (issueIds.isEmpty()) {
            return Map.of();
        }
        return reports.countPendingModerationByIssue(issueIds, ClassificationStatus.NEEDS_REVIEW).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
    }
}
