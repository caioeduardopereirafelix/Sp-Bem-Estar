package com.br.spbemestar.service;

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
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Identidade do cidadão, emitida no login por código: um JWT de 15 minutos para enviar relatos e um refresh token de 90 dias
 * para renová-lo. O JWT não pode ser revogado, por isso é curto; o refresh token fica no banco (só o hash)
 * e é trocado a cada uso. Se um token já trocado aparece de novo, alguém o copiou: todos os tokens daquele
 * cidadão são revogados.
 */
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

    /** Um par novo para o cidadão que acabou de confirmar o e-mail. */
    @Transactional
    public ReporterTokenResponse issueFor(UUID reporterId) {
        event("registered");
        return issue(reporterId, now());
    }

    // As revogações precisam ficar gravadas mesmo quando a renovação é recusada
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

    /**
     * Bloqueia quem enviou o relato: novos relatos e renovações dele são recusados na hora, inclusive com um
     * JWT ainda válido. Para voltar, precisaria de outro e-mail e de confirmá-lo.
     */
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
        String accessToken = jwt.encoder()
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();

        byte[] random = new byte[32];
        RANDOM.nextBytes(random);
        String refreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        Instant refreshExpiresAt = now.plus(properties.refreshTokenTtl());
        refreshTokens.save(ReporterRefreshToken.of(reporterId, ReporterHasher.tokenHash(refreshToken), now,
                refreshExpiresAt));
        return new ReporterTokenResponse(accessToken, "Bearer", accessExpiresAt, refreshToken, refreshExpiresAt);
    }

    /** Emissões, renovações, reusos e bloqueios: um pico de "registered" num IP indica alguém trocando de identidade. */
    private void event(String name) {
        meters.counter("spbemestar.reporters.events", "event", name).increment();
    }

    private Instant now() {
        return Instant.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }
}
