package com.br.spbemestar.service;

import com.br.spbemestar.config.TraceContext;
import com.br.spbemestar.exception.ReporterBlockedException;
import com.br.spbemestar.exception.UnknownCategoryException;
import com.br.spbemestar.model.Category;
import com.br.spbemestar.model.Issue;
import com.br.spbemestar.model.OutboxEvent;
import com.br.spbemestar.model.Report;
import com.br.spbemestar.repository.BlockedReporterRepository;
import com.br.spbemestar.repository.CategoryRepository;
import com.br.spbemestar.repository.OutboxEventRepository;
import com.br.spbemestar.repository.ReportRepository;
import com.br.spbemestar.util.GeoPoints;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReportService {

    public static final String REPORT_CREATED = "ReportCreated";

    private final CategoryRepository categories;
    private final ReportRepository reports;
    private final OutboxEventRepository outbox;
    private final DeduplicationService deduplication;
    private final PriorityService priority;
    private final ExifStripper exifStripper;
    private final PhotoStorage photoStorage;
    private final ReporterHasher reporterHasher;
    private final BlockedReporterRepository blockedReporters;
    private final TransactionTemplate transaction;
    private final JsonMapper json;
    private final TraceContext traceContext;
    private final MeterRegistry meters;
    private final Clock clock;

    /**
     * @param reporterId {@code sub} do token do cidadão; só o HMAC dele é gravado no relato
     */
    public ReportResult create(UUID reporterId, String categoryCode, double latitude, double longitude,
                               byte[] photo) {
        GeoPoints.of(latitude, longitude); // valida as coordenadas antes de qualquer gravação
        String reporterHash = reporterHasher.reporterHash(reporterId);
        if (blockedReporters.existsById(reporterHash)) {
            throw new ReporterBlockedException();
        }
        if (categories.findByCodeAndActiveTrue(categoryCode).isEmpty()) {
            throw new UnknownCategoryException(categoryCode);
        }

        ExifStripper.SanitizedPhoto sanitized = exifStripper.strip(photo);
        String photoId = photoStorage.store(new ByteArrayInputStream(sanitized.content()), sanitized.contentType());

        try {
            ReportResult result = transaction.execute(
                    status -> persist(reporterHash, categoryCode, latitude, longitude, photoId));
            meters.counter("spbemestar.reports.created", "category", categoryCode).increment();
            return result;
        } catch (RuntimeException e) {
            photoStorage.delete(photoId);
            throw e;
        }
    }

    private ReportResult persist(String reporterHash, String categoryCode, double latitude, double longitude,
                                 String photoId) {
        Category category = categories.findByCodeAndActiveTrue(categoryCode)
                .orElseThrow(() -> new UnknownCategoryException(categoryCode));
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);

        DeduplicationService.Result dedup = deduplication.attachOrOpen(category, latitude, longitude, now);
        Issue issue = dedup.issue();

        Report report = reports.save(
                Report.of(issue, category, GeoPoints.of(latitude, longitude), photoId, reporterHash, now));

        String payload = json.writeValueAsString(Map.of(
                "reportId", report.getId(),
                "issueId", issue.getId(),
                "category", category.getCode(),
                "photoId", photoId));
        outbox.save(OutboxEvent.of(report.getId(), REPORT_CREATED, payload, now, traceContext.currentTraceParent()));
        priority.recalculate(issue.getId());

        return new ReportResult(report.getId(), issue.getId(), dedup.created(),
                issue.getReportCount(), issue.getStatus().name());
    }

    public record ReportResult(Long reportId, Long issueId, boolean newIssue, int issueReportCount,
                               String issueStatus) {
    }
}
