package com.br.spbemestar.service;

import com.br.spbemestar.dto.ModerationItem;
import com.br.spbemestar.dto.ModerationRequest;
import com.br.spbemestar.dto.ModerationResponse;
import com.br.spbemestar.dto.PageResponse;
import com.br.spbemestar.exception.ReportAlreadyModeratedException;
import com.br.spbemestar.exception.ReportWithoutReporterException;
import com.br.spbemestar.exception.ResourceNotFoundException;
import com.br.spbemestar.model.BlockedReporter;
import com.br.spbemestar.model.Category;
import com.br.spbemestar.model.ClassificationStatus;
import com.br.spbemestar.model.Issue;
import com.br.spbemestar.model.ModerationDecision;
import com.br.spbemestar.model.Report;
import com.br.spbemestar.repository.BlockedReporterRepository;
import com.br.spbemestar.repository.CategoryRepository;
import com.br.spbemestar.repository.IssueStatusHistoryRepository;
import com.br.spbemestar.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Fila de moderação: relatos em que o classificador discordou da categoria ou teve pouca confiança.
 * Aprovar mantém o relato. Rejeitar tira o relato do chamado e apaga a foto; o chamado que fica sem
 * relatos válidos é descartado e some do mapa.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModerationService {

    private final ReportRepository reports;
    private final CategoryRepository categories;
    private final BlockedReporterRepository blockedReporters;
    private final IssueStatusHistoryRepository history;
    private final ReporterTokenService reporterTokens;
    private final PriorityService priority;
    private final PhotoStorage photoStorage;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<ModerationItem> pending(int page, int size) {
        Page<Report> result = reports.findPendingModeration(ClassificationStatus.NEEDS_REVIEW, PageRequest.of(page, size));
        Map<String, String> categoryNames = categories.findAll().stream()
                .collect(Collectors.toMap(Category::getCode, Category::getName));
        Set<String> blocked = blockedReporters.findAllById(result.getContent().stream()
                        .map(Report::getReporterHash).filter(Objects::nonNull).distinct().toList())
                .stream().map(BlockedReporter::getReporterHash).collect(Collectors.toSet());

        return PageResponse.of(result, report -> new ModerationItem(
                report.getId(),
                report.getIssue().getId(),
                report.getIssue().getStatus().name(),
                report.getCategory().getCode(),
                report.getCategory().getName(),
                report.getPredictedCategory(),
                categoryNames.get(report.getPredictedCategory()),
                report.getConfidence(),
                report.getClassificationStatus().name(),
                "/api/v1/photos/" + report.getPhotoId(),
                report.getCreatedAt(),
                report.getReporterHash() != null && blocked.contains(report.getReporterHash())));
    }

    @Transactional
    public ModerationResponse decide(Long reportId, ModerationRequest request, Long operatorId) {
        Report report = reports.findForModeration(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Relato", reportId));
        if (report.isModerated()) {
            throw new ReportAlreadyModeratedException(reportId, report.getModerationStatus().name());
        }
        if (request.blockReporter() && report.getReporterHash() == null) {
            throw new ReportWithoutReporterException(reportId);
        }
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        report.moderate(request.decision(), now, operatorId);

        Issue issue = report.getIssue();
        if (request.decision() == ModerationDecision.REJECTED) {
            issue.removeRejectedReport(now, operatorId)
                    .ifPresentOrElse(history::save, () -> priority.recalculate(issue.getId()));
            deletePhotoAfterCommit(report.getPhotoId());
            if (request.blockReporter()) {
                reporterTokens.blockAuthorOf(reportId, operatorId);
            }
        }
        log.info("Relato {} {} pelo operador {}{}", reportId, request.decision(), operatorId,
                request.blockReporter() ? ", autor bloqueado" : "");

        boolean reporterBlocked = report.getReporterHash() != null
                && blockedReporters.existsById(report.getReporterHash());
        return new ModerationResponse(reportId, request.decision().name(), issue.getId(), issue.getStatus().name(),
                issue.getReportCount(), reporterBlocked);
    }

    // O GridFS não participa da transação: a foto só some depois que a decisão foi gravada
    private void deletePhotoAfterCommit(String photoId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                photoStorage.delete(photoId);
            }
        });
    }
}
