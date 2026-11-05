package com.br.spbemestar.service;

import com.br.spbemestar.config.Jwts;
import com.br.spbemestar.config.ReporterJwt;
import com.br.spbemestar.config.ReporterProperties;
import com.br.spbemestar.config.SecurityProperties;
import com.br.spbemestar.dto.ReporterTokenResponse;
import com.br.spbemestar.exception.InvalidRefreshTokenException;
import com.br.spbemestar.exception.ReportWithoutReporterException;
import com.br.spbemestar.exception.ReporterBlockedException;
import com.br.spbemestar.exception.ResourceNotFoundException;
import com.br.spbemestar.model.BlockedReporter;
import com.br.spbemestar.model.Report;
import com.br.spbemestar.model.ReporterRefreshToken;
import com.br.spbemestar.repository.BlockedReporterRepository;
import com.br.spbemestar.repository.ReportRepository;
import com.br.spbemestar.repository.ReporterRefreshTokenRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReporterTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ReporterRefreshTokenRepository refreshTokens;
    private final BlockedReporterRepository blockedReporters;
    private final ReportRepository reports;
    private final ReporterJwt jwt;
    private final ReporterHasher hasher;
    private final ReporterProperties properties;
    private final SecurityProperties security;
    private final Clock clock;
    private final MeterRegistry meters;

    @Transactional
    public ReporterTokenResponse issueFor(UUID reporterId) {
        event("registered");
        return issue(reporterId, now());
    }

    @Transactional(noRollbackFor = {InvalidRefreshTokenException.class, ReporterBlockedException.class})
    public ReporterTokenResponse refresh(String refreshToken) {
        Instant now = now();
        ReporterRefreshToken current = refreshTokens.findByTokenHash(ReporterHasher.tokenHash(refreshToken))
                .orElseThrow(InvalidRefreshTokenException::new);
        if (current.isSpent()) {
            int revoked = refreshTokens.revokeAllOf(current.getReporterId(), now);
            log.warn("Refresh token reutilizado; {} tokens do mesmo cidadão revogados", revoked);
            event("refresh_reused");
            throw new InvalidRefreshTokenException();
        }
        if (current.isExpired(now)) {
            throw new InvalidRefreshTokenException();
        }
        if (isBlocked(current.getReporterId())) {
            refreshTokens.revokeAllOf(current.getReporterId(), now);
            event("refresh_blocked");
            throw new ReporterBlockedException();
        }
        current.markUsed(now);
        event("refreshed");
        return issue(current.getReporterId(), now);
    }

    public boolean isBlocked(UUID reporterId) {
        return blockedReporters.existsById(hasher.reporterHash(reporterId));
    }

    @Transactional
    public void blockAuthorOf(Long reportId, Long operatorId) {
        Report report = reports.findById(reportId).orElseThrow(() -> new ResourceNotFoundException("Relato", reportId));
        String reporterHash = report.getReporterHash();
        if (reporterHash == null) {
            throw new ReportWithoutReporterException(reportId);
        }
        if (!blockedReporters.existsById(reporterHash)) {
            blockedReporters.save(BlockedReporter.of(reporterHash, now(), operatorId, reportId));
            log.info("Autor do relato {} bloqueado pelo operador {}", reportId, operatorId);
            event("blocked");
        }
    }

    @Scheduled(cron = "${app.reporter.cleanup-cron:0 30 3 * * *}")
    @Transactional
    public int deleteExpiredRefreshTokens() {
        int deleted = refreshTokens.deleteExpired(now());
        if (deleted > 0) {
            log.info("{} refresh tokens vencidos apagados", deleted);
        }
        return deleted;
    }

    private ReporterTokenResponse issue(UUID reporterId, Instant now) {
        Instant accessExpiresAt = now.plus(properties.accessTokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(security.issuer())
                .audience(List.of(properties.audience()))
                .subject(reporterId.toString())
                .issuedAt(now)
                .expiresAt(accessExpiresAt)
                .build();
        String accessToken = Jwts.sign(jwt.encoder(), claims);

        byte[] random = new byte[32];
        RANDOM.nextBytes(random);
        String refreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        Instant refreshExpiresAt = now.plus(properties.refreshTokenTtl());
        refreshTokens.save(ReporterRefreshToken.of(reporterId, ReporterHasher.tokenHash(refreshToken), now,
                refreshExpiresAt));
        return new ReporterTokenResponse(accessToken, "Bearer", accessExpiresAt, refreshToken, refreshExpiresAt);
    }

    private void event(String name) {
        meters.counter("spbemestar.reporters.events", "event", name).increment();
    }

    private Instant now() {
        return Instant.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }
}
