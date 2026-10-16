package com.br.spbemestar.model;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Relato individual de um cidadão. Vários relatos podem apontar para o mesmo {@link Issue}.
 */
@Entity
@Table(name = "report")
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "issue_id")
    private Issue issue;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_code")
    private Category category;

    @JdbcTypeCode(SqlTypes.GEOGRAPHY)
    @Column(nullable = false, columnDefinition = "geography(Point, 4326)")
    private Point location;

    /** ObjectId do arquivo no GridFS. */
    @Column(name = "photo_id", nullable = false)
    private String photoId;

    /** HMAC do identificador do autor; nunca o dado pessoal em si. Nulo para relatos anônimos. */
    @Column(name = "reporter_hash")
    private String reporterHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "classification_status", nullable = false)
    private ClassificationStatus classificationStatus;

    @Column(name = "predicted_category")
    private String predictedCategory;

    @Column(precision = 4, scale = 3)
    private BigDecimal confidence;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Report() {
    }

    private Report(Issue issue, Category category, Point location, String photoId, String reporterHash,
                   Instant createdAt) {
        this.issue = Objects.requireNonNull(issue);
        this.category = Objects.requireNonNull(category);
        this.location = Objects.requireNonNull(location);
        this.photoId = Objects.requireNonNull(photoId);
        this.reporterHash = reporterHash;
        this.classificationStatus = ClassificationStatus.PENDING;
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    public static Report of(Issue issue, Category category, Point location, String photoId, String reporterHash,
                            Instant createdAt) {
        return new Report(issue, category, location, photoId, reporterHash, createdAt);
    }

    /**
     * Aplica o resultado do classificador comparando com a categoria informada pelo cidadão.
     */
    public void applyClassification(String predicted, BigDecimal confidence, BigDecimal minimumConfidence) {
        this.predictedCategory = predicted;
        this.confidence = confidence;
        if (confidence.compareTo(minimumConfidence) < 0) {
            this.classificationStatus = ClassificationStatus.LOW_CONFIDENCE;
        } else if (category.getCode().equals(predicted)) {
            this.classificationStatus = ClassificationStatus.AGREED;
        } else {
            this.classificationStatus = ClassificationStatus.MISMATCH;
        }
    }

    public Long getId() {
        return id;
    }

    public Issue getIssue() {
        return issue;
    }

    public Category getCategory() {
        return category;
    }

    public Point getLocation() {
        return location;
    }

    public String getPhotoId() {
        return photoId;
    }

    public String getReporterHash() {
        return reporterHash;
    }

    public ClassificationStatus getClassificationStatus() {
        return classificationStatus;
    }

    public String getPredictedCategory() {
        return predictedCategory;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
