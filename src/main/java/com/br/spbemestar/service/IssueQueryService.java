package com.br.spbemestar.service;

import com.br.spbemestar.dto.BoundingBox;
import com.br.spbemestar.dto.GeoJsonFeatureCollection;
import com.br.spbemestar.dto.IssueDetailResponse;
import com.br.spbemestar.exception.ResourceNotFoundException;
import com.br.spbemestar.model.AppUser;
import com.br.spbemestar.model.Issue;
import com.br.spbemestar.model.IssueStatus;
import com.br.spbemestar.model.IssueStatusHistory;
import com.br.spbemestar.model.Neighborhood;
import com.br.spbemestar.repository.AppUserRepository;
import com.br.spbemestar.repository.IssueQueryRepository;
import com.br.spbemestar.repository.IssueRepository;
import com.br.spbemestar.repository.IssueStatusHistoryRepository;
import com.br.spbemestar.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IssueQueryService {

    private final IssueQueryRepository issueQueries;
    private final IssueRepository issues;
    private final ReportRepository reports;
    private final IssueStatusHistoryRepository history;
    private final AppUserRepository users;

    public GeoJsonFeatureCollection search(BoundingBox bbox, String category, IssueStatus status, int limit) {
        return GeoJsonFeatureCollection.of(issueQueries.search(bbox, category, status, limit));
    }

    /**
     * @param showOperators inclui o nome de quem mudou cada status (só para operadores)
     */
    @Transactional(readOnly = true)
    public IssueDetailResponse detail(Long id, boolean showOperators) {
        Issue issue = issues.findById(id)
                .filter(i -> i.getStatus() != IssueStatus.DISCARDED) // descartado pela moderação: some do mapa
                .orElseThrow(() -> new ResourceNotFoundException("Chamado", id));
        Neighborhood neighborhood = issue.getNeighborhood();

        var reportItems = reports.findVisibleByIssue(id).stream()
                .map(r -> new IssueDetailResponse.ReportItem(r.getId(), r.getCreatedAt(),
                        "/api/v1/photos/" + r.getPhotoId(), r.getClassificationStatus().name()))
                .toList();
        List<IssueStatusHistory> changes = history.findByIssueIdOrderByChangedAt(id);
        Map<Long, String> operatorNames = showOperators
                ? users.findAllById(changes.stream().map(IssueStatusHistory::getChangedBy).filter(Objects::nonNull)
                        .distinct().toList()).stream().collect(Collectors.toMap(AppUser::getId, AppUser::getName))
                : Map.of();
        var statusChanges = changes.stream()
                .map(h -> new IssueDetailResponse.StatusChange(
                        h.getFromStatus() == null ? null : h.getFromStatus().name(),
                        h.getToStatus().name(), h.getChangedAt(),
                        h.getChangedBy() == null ? null : operatorNames.get(h.getChangedBy())))
                .toList();

        return new IssueDetailResponse(
                issue.getId(),
                issue.getCategory().getCode(),
                issue.getCategory().getName(),
                issue.getStatus().name(),
                issue.getLocation().getY(),
                issue.getLocation().getX(),
                neighborhood == null ? null : neighborhood.getName(),
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
}
