package com.br.spbemestar.service;

import com.br.spbemestar.config.PhotoPrecheckProperties;
import com.br.spbemestar.exception.PhotoCategoryMismatchException;
import com.br.spbemestar.exception.ReporterBlockedException;
import com.br.spbemestar.exception.UnknownCategoryException;
import com.br.spbemestar.model.Category;
import com.br.spbemestar.model.Issue;
import com.br.spbemestar.model.Report;
import com.br.spbemestar.repository.BlockedReporterRepository;
import com.br.spbemestar.repository.CategoryRepository;
import com.br.spbemestar.repository.ReportRepository;
import com.br.spbemestar.util.GeoPoints;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReportService {

    public static final String REPORT_CREATED = "ReportCreated";

    private final CategoryRepository categories;
    private final ReportRepository reports;
    private final OutboxWriter outbox;
    private final DeduplicationService deduplication;
    private final PriorityService priority;
    private final ExifStripper exifStripper;
    private final PhotoStorage photoStorage;
    private final PhotoPrecheck photoPrecheck;
    private final PhotoPrecheckProperties precheckProperties;
    private final ReporterHasher reporterHasher;
    private final BlockedReporterRepository blockedReporters;
    private final TransactionTemplate transaction;
    private final MeterRegistry meters;
    private final Clock clock;

    public ReportResult create(UUID reporterId, String categoryCode, double latitude, double longitude,
                               byte[] photo) {
        return create(reporterId, categoryCode, latitude, longitude, photo, false);
    }

    public ReportResult create(UUID reporterId, String categoryCode, double latitude, double longitude,
                               byte[] photo, boolean confirmCategory) {
        GeoPoints.of(latitude, longitude);
        String reporterHash = reporterHasher.reporterHash(reporterId);
        if (blockedReporters.existsById(reporterHash)) {
            throw new ReporterBlockedException();
        }
        if (categories.findByCodeAndActiveTrue(categoryCode).isEmpty()) {
            throw new UnknownCategoryException(categoryCode);
        }

        ExifStripper.SanitizedPhoto sanitized = exifStripper.strip(photo);
        if (confirmCategory) {
            precheckOutcome("insisted");
        } else {
            precheck(categoryCode, sanitized);
        }
        String photoId = photoStorage.store(sanitized);

        try {
            ReportResult result = transaction.execute(
                    status -> persist(reporterHash, categoryCode, latitude, longitude, photoId));
            meters.counter("spbemestar.reports.received", "category", categoryCode).increment();
            return result;
        } catch (RuntimeException e) {
            photoStorage.delete(photoId);
            throw e;
        }
    }

    private void precheck(String categoryCode, ExifStripper.SanitizedPhoto sanitized) {
        Optional<PhotoPrecheck.Prediction> prediction = photoPrecheck.classify(sanitized.content(), sanitized.contentType());
        if (prediction.isEmpty()) {
            return;
        }
        PhotoPrecheck.Prediction seen = prediction.get();
        if (seen.category().equals(categoryCode) || seen.confidence() < precheckProperties.rejectConfidence()) {
            precheckOutcome("passed");
            return;
        }
        boolean notAProblem = PhotoPrecheck.NOT_A_PROBLEM.equals(seen.category());
        String predictedName = notAProblem ? null
                : categories.findByCodeAndActiveTrue(seen.category()).map(Category::getName).orElse(null);
        if (!notAProblem && predictedName == null) {
            precheckOutcome("passed");
            return;
        }
        precheckOutcome("rejected");
        throw new PhotoCategoryMismatchException(categoryCode, seen.category(), predictedName, seen.confidence());
    }

    private void precheckOutcome(String outcome) {
        meters.counter("spbemestar.reports.precheck", "outcome", outcome).increment();
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

        outbox.append(report.getId(), REPORT_CREATED, Map.of(
                "reportId", report.getId(),
                "issueId", issue.getId(),
                "category", category.getCode(),
                "photoId", photoId), now);
        priority.recalculate(issue.getId());

        return new ReportResult(report.getId(), issue.getId(), dedup.created(),
                issue.getReportCount(), issue.getStatus().name());
    }

    public record ReportResult(Long reportId, Long issueId, boolean newIssue, int issueReportCount,
                               String issueStatus) {
    }
}
