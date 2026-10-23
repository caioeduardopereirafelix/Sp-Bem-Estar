package com.br.spbemestar.repository;

import com.br.spbemestar.TestcontainersConfiguration;
import com.br.spbemestar.exception.InvalidStatusTransitionException;
import com.br.spbemestar.model.Category;
import com.br.spbemestar.model.ClassificationStatus;
import com.br.spbemestar.model.Issue;
import com.br.spbemestar.model.IssueStatus;
import com.br.spbemestar.model.Neighborhood;
import com.br.spbemestar.model.OutboxEvent;
import com.br.spbemestar.model.Report;
import com.br.spbemestar.util.GeoPoints;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Limit;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class DomainPersistenceTests {

    // Esquina da Av. Paulista com a R. Augusta
    static final double LAT = -23.5614;
    static final double LON = -46.6544;

    @Autowired EntityManager em;
    @Autowired CategoryRepository categories;
    @Autowired NeighborhoodRepository neighborhoods;
    @Autowired IssueRepository issues;
    @Autowired IssueStatusHistoryRepository history;
    @Autowired ReportRepository reports;
    @Autowired OutboxEventRepository outbox;

    @Test
    void persistsIssueAndReportWithGeographyPoint() {
        Category pothole = categories.findById("POTHOLE").orElseThrow();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

        Issue issue = issues.save(Issue.open(pothole, GeoPoints.of(LAT, LON), null, now));
        reports.save(Report.of(issue, pothole, GeoPoints.of(LAT, LON), "photo-1", null, now));
        flushAndClear();

        Issue loaded = issues.findById(issue.getId()).orElseThrow();
        assertThat(loaded.getLocation().getY()).isEqualTo(LAT);
        assertThat(loaded.getLocation().getX()).isEqualTo(LON);
        assertThat(loaded.getStatus()).isEqualTo(IssueStatus.OPEN);
        assertThat(loaded.getReportCount()).isEqualTo(1);
        assertThat(loaded.getFirstReportedAt()).isEqualTo(now);

        assertThat(reports.findByIssueIdOrderByCreatedAt(issue.getId()))
                .singleElement()
                .satisfies(r -> assertThat(r.getClassificationStatus()).isEqualTo(ClassificationStatus.PENDING));
    }

    @Test
    void storedLocationIsUsableInGeographyQueries() {
        Category pothole = categories.findById("POTHOLE").orElseThrow();
        Issue issue = issues.save(Issue.open(pothole, GeoPoints.of(LAT, LON), null, Instant.now()));
        flushAndClear();

        // ~11 m ao sul: dentro de 15 m, fora de 5 m
        Object[] distances = (Object[]) em.createNativeQuery("""
                SELECT ST_DWithin(location, ST_MakePoint(:lon, :lat)::geography, 15),
                       ST_DWithin(location, ST_MakePoint(:lon, :lat)::geography, 5)
                FROM issue WHERE id = :id
                """)
                .setParameter("lon", LON)
                .setParameter("lat", LAT - 0.0001)
                .setParameter("id", issue.getId())
                .getSingleResult();

        assertThat(distances).containsExactly(true, false);
    }

    @Test
    void registerReportIncrementsCounter() {
        Category trash = categories.findById("TRASH").orElseThrow();
        Instant first = Instant.parse("2026-10-01T10:00:00Z");
        Issue issue = issues.save(Issue.open(trash, GeoPoints.of(LAT, LON), null, first));

        issue.registerReport(first.plus(2, ChronoUnit.HOURS));
        flushAndClear();

        Issue loaded = issues.findById(issue.getId()).orElseThrow();
        assertThat(loaded.getReportCount()).isEqualTo(2);
        assertThat(loaded.getLastReportedAt()).isEqualTo(first.plus(2, ChronoUnit.HOURS));
        assertThat(loaded.getVersion()).isEqualTo(1);
    }

    @Test
    void statusChangeIsRecordedInHistory() {
        Category pothole = categories.findById("POTHOLE").orElseThrow();
        Issue issue = issues.save(Issue.open(pothole, GeoPoints.of(LAT, LON), null, Instant.now()));

        history.save(issue.changeStatus(IssueStatus.CONFIRMED, Instant.now(), null));
        history.save(issue.changeStatus(IssueStatus.RESOLVED, Instant.now(), null));
        flushAndClear();

        Issue loaded = issues.findById(issue.getId()).orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(IssueStatus.RESOLVED);
        assertThat(loaded.getResolvedAt()).isNotNull();
        assertThat(history.findByIssueIdOrderByChangedAt(issue.getId()))
                .extracting(h -> h.getFromStatus() + "->" + h.getToStatus())
                .containsExactly("OPEN->CONFIRMED", "CONFIRMED->RESOLVED");
    }

    @Test
    void resolvedIssueRejectsNewReportsAndTransitions() {
        Category pothole = categories.findById("POTHOLE").orElseThrow();
        Issue issue = Issue.open(pothole, GeoPoints.of(LAT, LON), null, Instant.now());
        issue.changeStatus(IssueStatus.RESOLVED, Instant.now(), null);

        assertThatThrownBy(() -> issue.registerReport(Instant.now()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> issue.changeStatus(IssueStatus.OPEN, Instant.now(), null))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void findsNeighborhoodContainingPoint() throws ParseException {
        // Quadrado simples em volta do ponto de teste
        MultiPolygon area = (MultiPolygon) new WKTReader(GeoPoints.factory()).read("""
                MULTIPOLYGON(((-46.66 -23.57, -46.65 -23.57, -46.65 -23.55, -46.66 -23.55, -46.66 -23.57)))
                """);
        Neighborhood belaVista = neighborhoods.save(new Neighborhood("Bela Vista", "São Paulo", 69460, area));
        flushAndClear();

        assertThat(neighborhoods.findContaining(LAT, LON))
                .hasValueSatisfying(n -> assertThat(n.getId()).isEqualTo(belaVista.getId()));
        assertThat(neighborhoods.findContaining(-23.60, -46.70)).isEmpty();
    }

    @Test
    void persistsOutboxEventWithJsonbPayload() {
        // Data antiga: o evento fica à frente dos que outros testes deixaram pendentes no mesmo banco
        OutboxEvent event = outbox.save(OutboxEvent.of(
                42L, "ReportCreated", "{\"reportId\": 42, \"photoId\": \"abc\"}", Instant.parse("2000-01-01T00:00:00Z"), null));
        flushAndClear();

        assertThat(outbox.findByPublishedAtIsNullOrderByCreatedAt(Limit.of(10)))
                .extracting(OutboxEvent::getId)
                .contains(event.getId());

        String photoId = (String) em.createNativeQuery("SELECT payload ->> 'photoId' FROM outbox_event WHERE id = :id")
                .setParameter("id", event.getId())
                .getSingleResult();
        assertThat(photoId).isEqualTo("abc");
    }

    @Test
    void appliesClassificationResult() {
        Category pothole = categories.findById("POTHOLE").orElseThrow();
        Issue issue = Issue.open(pothole, GeoPoints.of(LAT, LON), null, Instant.now());
        BigDecimal threshold = new BigDecimal("0.700");

        Report agreed = Report.of(issue, pothole, GeoPoints.of(LAT, LON), "p1", null, Instant.now());
        agreed.applyClassification("POTHOLE", new BigDecimal("0.910"), threshold);

        Report mismatch = Report.of(issue, pothole, GeoPoints.of(LAT, LON), "p2", null, Instant.now());
        mismatch.applyClassification("TRASH", new BigDecimal("0.880"), threshold);

        Report unsure = Report.of(issue, pothole, GeoPoints.of(LAT, LON), "p3", null, Instant.now());
        unsure.applyClassification("POTHOLE", new BigDecimal("0.400"), threshold);

        assertThat(agreed.getClassificationStatus()).isEqualTo(ClassificationStatus.AGREED);
        assertThat(mismatch.getClassificationStatus()).isEqualTo(ClassificationStatus.MISMATCH);
        assertThat(unsure.getClassificationStatus()).isEqualTo(ClassificationStatus.LOW_CONFIDENCE);
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }
}
