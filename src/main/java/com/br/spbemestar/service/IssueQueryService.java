package com.br.spbemestar.service;

import com.br.spbemestar.dto.BoundingBox;
import com.br.spbemestar.dto.GeoJsonFeatureCollection;
import com.br.spbemestar.dto.IssueDetailResponse;
import com.br.spbemestar.exception.ResourceNotFoundException;
import com.br.spbemestar.model.Issue;
import com.br.spbemestar.model.IssueStatus;
import com.br.spbemestar.model.Neighborhood;
import com.br.spbemestar.repository.IssueQueryRepository;
import com.br.spbemestar.repository.IssueRepository;
import com.br.spbemestar.repository.IssueStatusHistoryRepository;
import com.br.spbemestar.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IssueQueryService {

    private final IssueQueryRepository issueQueries;
    private final IssueRepository issues;
    private final ReportRepository reports;
    private final IssueStatusHistoryRepository history;

    public GeoJsonFeatureCollection search(BoundingBox bbox, String category, IssueStatus status, int limit) {
        return GeoJsonFeatureCollection.of(issueQueries.search(bbox, category, status, limit));
    }

    @Transactional(readOnly = true)
    public IssueDetailResponse detail(Long id) {
        Issue issue = issues.findById(id).orElseThrow(() -> new ResourceNotFoundException("Chamado", id));
        Neighborhood neighborhood = issue.getNeighborhood();

        var reportItems = reports.findByIssueIdOrderByCreatedAt(id).stream()
                .map(r -> new IssueDetailResponse.ReportItem(r.getId(), r.getCreatedAt(),
                        "/api/v1/photos/" + r.getPhotoId(), r.getClassificationStatus().name()))
                .toList();
        var statusChanges = history.findByIssueIdOrderByChangedAt(id).stream()
                .map(h -> new IssueDetailResponse.StatusChange(
                        h.getFromStatus() == null ? null : h.getFromStatus().name(),
                        h.getToStatus().name(), h.getChangedAt()))
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
                issue.getPriorityScore(),
                issue.getFirstReportedAt(),
                issue.getLastReportedAt(),
                issue.getResolvedAt(),
                reportItems,
                statusChanges);
    }
}
