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
import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "reporter_refresh_token")
public class ReporterRefreshToken {

    @Id
    private UUID id;

    @Column(name = "reporter_id", nullable = false)
    private UUID reporterId;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    private ReporterRefreshToken(UUID reporterId, String tokenHash, Instant issuedAt, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.reporterId = Objects.requireNonNull(reporterId);
        this.tokenHash = Objects.requireNonNull(tokenHash);
        this.issuedAt = Objects.requireNonNull(issuedAt);
        this.expiresAt = Objects.requireNonNull(expiresAt);
    }

    public static ReporterRefreshToken of(UUID reporterId, String tokenHash, Instant issuedAt, Instant expiresAt) {
        return new ReporterRefreshToken(reporterId, tokenHash, issuedAt, expiresAt);
    }

    /** Já trocado ou revogado: aparecer de novo indica que o token foi copiado por outra pessoa. */
    public boolean isSpent() {
        return usedAt != null || revokedAt != null;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public void markUsed(Instant now) {
        this.usedAt = now;
    }
}
