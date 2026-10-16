package com.br.spbemestar.model;

import com.br.spbemestar.exception.InvalidStatusTransitionException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Chamado consolidado: vários relatos do mesmo problema, no mesmo ponto, viram um único issue.
 */
@Entity
@Table(name = "issue")
public class Issue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_code")
    private Category category;

    // geography: ST_DWithin mede em metros reais, que é o que a deduplicação precisa
    @JdbcTypeCode(SqlTypes.GEOGRAPHY)
    @Column(nullable = false, columnDefinition = "geography(Point, 4326)")
    private Point location;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "neighborhood_id")
    private Neighborhood neighborhood;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IssueStatus status;

    @Column(name = "report_count", nullable = false)
    private int reportCount;

    @Column(name = "priority_score", nullable = false, precision = 10, scale = 4)
    private BigDecimal priorityScore;

    @Column(name = "first_reported_at", nullable = false)
    private Instant firstReportedAt;

    @Column(name = "last_reported_at", nullable = false)
    private Instant lastReportedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Version
    private long version;

    protected Issue() {
    }

    private Issue(Category category, Point location, Neighborhood neighborhood, Instant reportedAt) {
        this.category = Objects.requireNonNull(category);
        this.location = Objects.requireNonNull(location);
        this.neighborhood = neighborhood;
        this.status = IssueStatus.OPEN;
        this.reportCount = 1;
        this.priorityScore = BigDecimal.ZERO;
        this.firstReportedAt = Objects.requireNonNull(reportedAt);
        this.lastReportedAt = reportedAt;
    }

    public static Issue open(Category category, Point location, Neighborhood neighborhood, Instant reportedAt) {
        return new Issue(category, location, neighborhood, reportedAt);
    }

    /**
     * Registra mais um relato do mesmo problema.
     */
    public void registerReport(Instant reportedAt) {
        if (status == IssueStatus.RESOLVED) {
            throw new IllegalStateException("Chamado resolvido não recebe novos relatos: " + id);
        }
        reportCount++;
        if (reportedAt.isAfter(lastReportedAt)) {
            lastReportedAt = reportedAt;
        }
    }

    /**
     * Altera o status validando a transição e devolve o registro de histórico a ser persistido.
     */
    public IssueStatusHistory changeStatus(IssueStatus target, Instant changedAt) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidStatusTransitionException(status, target);
        }
        IssueStatus previous = status;
        status = target;
        if (target == IssueStatus.RESOLVED) {
            resolvedAt = changedAt;
        }
        return new IssueStatusHistory(this, previous, target, changedAt);
    }

    public void updatePriorityScore(BigDecimal score) {
        this.priorityScore = Objects.requireNonNull(score);
    }

    public boolean isOpen() {
        return status != IssueStatus.RESOLVED;
    }

    public Long getId() {
        return id;
    }

    public Category getCategory() {
        return category;
    }

    public Point getLocation() {
        return location;
    }

    public Neighborhood getNeighborhood() {
        return neighborhood;
    }

    public IssueStatus getStatus() {
        return status;
    }

    public int getReportCount() {
        return reportCount;
    }

    public BigDecimal getPriorityScore() {
        return priorityScore;
    }

    public Instant getFirstReportedAt() {
        return firstReportedAt;
    }

    public Instant getLastReportedAt() {
        return lastReportedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public long getVersion() {
        return version;
    }
}
