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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;


@Getter
@DynamicUpdate
@NoArgsConstructor
@Entity
@Table(name = "issue")
public class Issue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_code")
    private Category category;

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

    // pessoas distintas entre os relatos: calculado no banco junto com a prioridade (IssueRepository)
    @Column(name = "reporter_count", nullable = false, insertable = false, updatable = false)
    private int reporterCount;

    @Column(name = "priority_score", nullable = false, precision = 10, scale = 4)
    private BigDecimal priorityScore;

    @Column(name = "first_reported_at", nullable = false)
    private Instant firstReportedAt;

    @Column(name = "last_reported_at", nullable = false)
    private Instant lastReportedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    /** Foto do conserto, tirada pela equipe ao resolver; pública, como as fotos dos relatos. */
    @Column(name = "resolution_photo_id")
    private String resolutionPhotoId;

    /** O que foi feito, em poucas palavras, para quem relatou. */
    @Column(name = "resolution_note")
    private String resolutionNote;

    @Version
    private long version;

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

    public void registerReport(Instant reportedAt) {
        if (!isOpen()) {
            throw new IllegalStateException("Chamado " + status + " não recebe novos relatos: " + id);
        }
        reportCount++;
        if (reportedAt.isAfter(lastReportedAt)) {
            lastReportedAt = reportedAt;
        }
    }

    public IssueStatusHistory changeStatus(IssueStatus target, Instant changedAt, Long operatorId) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidStatusTransitionException(status, target);
        }
        IssueStatus previous = status;
        status = target;
        if (target == IssueStatus.RESOLVED) {
            resolvedAt = changedAt;
        }
        return new IssueStatusHistory(this, previous, target, changedAt, operatorId);
    }

    /** Resolve o chamado com o que a equipe mostra ao cidadão: a foto do conserto e uma nota, os dois opcionais. */
    public IssueStatusHistory resolve(Instant changedAt, Long operatorId, String photoId, String note) {
        IssueStatusHistory change = changeStatus(IssueStatus.RESOLVED, changedAt, operatorId);
        resolutionPhotoId = photoId;
        resolutionNote = note;
        return change;
    }

    /**
     * Tira do chamado um relato rejeitado pela moderação. Sem nenhum relato válido, o chamado é descartado.
     *
     * @return a mudança de status, quando o chamado é descartado
     */
    public Optional<IssueStatusHistory> removeRejectedReport(Instant changedAt, Long operatorId) {
        if (status == IssueStatus.DISCARDED) {
            throw new IllegalStateException("Chamado já descartado: " + id);
        }
        reportCount--;
        if (reportCount > 0) {
            return Optional.empty();
        }
        IssueStatus previous = status;
        status = IssueStatus.DISCARDED;
        resolvedAt = null;
        return Optional.of(new IssueStatusHistory(this, previous, IssueStatus.DISCARDED, changedAt, operatorId));
    }

    public void updatePriorityScore(BigDecimal score) {
        this.priorityScore = Objects.requireNonNull(score);
    }

    public boolean isOpen() {
        return status != IssueStatus.RESOLVED && status != IssueStatus.DISCARDED;
    }
}
