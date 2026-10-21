package com.br.spbemestar.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "blocked_reporter")
public class BlockedReporter {

    @Id
    @Column(name = "reporter_hash")
    private String reporterHash;

    @Column(name = "blocked_at", nullable = false)
    private Instant blockedAt;

    @Column(name = "blocked_by", nullable = false)
    private Long blockedBy;

    @Column(name = "report_id")
    private Long reportId;

    private BlockedReporter(String reporterHash, Instant blockedAt, Long blockedBy, Long reportId) {
        this.reporterHash = Objects.requireNonNull(reporterHash);
        this.blockedAt = Objects.requireNonNull(blockedAt);
        this.blockedBy = Objects.requireNonNull(blockedBy);
        this.reportId = reportId;
    }

    public static BlockedReporter of(String reporterHash, Instant blockedAt, Long blockedBy, Long reportId) {
        return new BlockedReporter(reporterHash, blockedAt, blockedBy, reportId);
    }
}
