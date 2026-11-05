package com.br.spbemestar.service;

import com.br.spbemestar.config.LoginProperties;
import com.br.spbemestar.config.ReporterJwt;
import com.br.spbemestar.config.SecurityConfig;
import com.br.spbemestar.config.SecurityProperties;
import com.br.spbemestar.dto.LoginCodeSentResponse;
import com.br.spbemestar.dto.LoginResponse;
import com.br.spbemestar.exception.InvalidLoginCodeException;
import com.br.spbemestar.exception.LoginCodeThrottledException;
import com.br.spbemestar.exception.StaffAccessDisabledException;
import com.br.spbemestar.model.AppUser;
import com.br.spbemestar.model.LoginCode;
import com.br.spbemestar.repository.AppUserRepository;
import com.br.spbemestar.repository.LoginCodeRepository;
import com.br.spbemestar.service.mail.EmailSender;
import com.br.spbemestar.service.mail.LoginCodeEmail;
import com.br.spbemestar.util.Crypto;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class LoginCodeService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final LoginCodeRepository codes;
    private final AppUserRepository users;
    private final AuthService auth;
    private final CitizenAccountService citizens;
    private final EmailSender mail;
    private final ReporterJwt reporterJwt;
    private final LoginProperties properties;
    private final Clock clock;
    private final MeterRegistry meters;
    private final SecretKey codeKey;

    public LoginCodeService(LoginCodeRepository codes, AppUserRepository users, AuthService auth,
                            CitizenAccountService citizens, EmailSender mail, ReporterJwt reporterJwt,
                            LoginProperties properties, SecurityProperties security, Clock clock,
                            MeterRegistry meters) {
        this.codes = codes;
        this.users = users;
        this.auth = auth;
        this.citizens = citizens;
        this.mail = mail;
        this.reporterJwt = reporterJwt;
        this.properties = properties;
        this.clock = clock;
        this.meters = meters;
        this.codeKey = Crypto.deriveKey(SecurityConfig.secretKey(security), "spbemestar/login-code/v1");
    }

    @Transactional
    public LoginCodeSentResponse requestCode(String rawEmail) {
        String email = AppUser.normalizeEmail(rawEmail);
        Instant now = now();
        throttle(email, now);

        String code = "%06d".formatted(RANDOM.nextInt(1_000_000));
        codes.invalidatePending(email, now);
        LoginCode saved = codes.save(LoginCode.of(email, hash(email, code), now, now.plus(properties.codeTtl())));
        mail.send(LoginCodeEmail.to(email, code, properties.codeTtl()));
        codeEvent("sent");
        return new LoginCodeSentResponse(saved.getExpiresAt(), properties.resendInterval().toSeconds());
    }

    @Transactional(noRollbackFor = InvalidLoginCodeException.class)
    public LoginResponse verify(String rawEmail, String code, String previousReporterToken) {
        String email = AppUser.normalizeEmail(rawEmail);
        Instant now = now();
        LoginCode pending = codes.findFirstByEmailAndConsumedAtIsNullOrderByCreatedAtDesc(email)
                .filter(c -> c.isUsable(now, properties.maxAttempts()))
                .orElseThrow(() -> failure());
        if (!matches(pending, email, code)) {
            pending.failedAttempt(now, properties.maxAttempts());
            throw failure();
        }
        pending.consume(now);

        LoginResponse response = users.findByEmail(email)
                .map(staff -> staffLogin(staff, email))
                .orElseGet(() -> citizenLogin(email, previousReporterToken));
        login("success");
        return response;
    }

    @Scheduled(cron = "${app.login.cleanup-cron:0 40 3 * * *}")
    @Transactional
    public int deleteOldCodes() {
        return codes.deleteExpiredBefore(now().minus(Duration.ofDays(1)));
    }

    private void throttle(String email, Instant now) {
        Optional<Instant> nextAllowed = codes.findFirstByEmailOrderByCreatedAtDesc(email)
                .map(last -> last.getCreatedAt().plus(properties.resendInterval()))
                .filter(next -> next.isAfter(now));
        if (nextAllowed.isPresent()) {
            codeEvent("throttled");
            throw new LoginCodeThrottledException("Espere um pouco para pedir outro código.",
                    Duration.between(now, nextAllowed.get()).toSeconds() + 1);
        }
        long lastHour = codes.countByEmailAndCreatedAtAfter(email, now.minus(Duration.ofHours(1)));
        if (lastHour >= properties.maxCodesPerHour()) {
            codeEvent("throttled");
            throw new LoginCodeThrottledException("Muitos códigos pedidos para este e-mail. Tente de novo mais tarde.",
                    Duration.ofHours(1).toSeconds());
        }
    }

    private boolean matches(LoginCode pending, String email, String code) {
        return MessageDigest.isEqual(pending.getCodeHash().getBytes(StandardCharsets.UTF_8),
                hash(email, code).getBytes(StandardCharsets.UTF_8));
    }

    private LoginResponse staffLogin(AppUser staff, String email) {
        if (!staff.isActive()) {
            login("failure");
            throw new StaffAccessDisabledException();
        }
        log.info("Usuário {} ({}) entrou", staff.getId(), staff.getRole());
        return LoginResponse.staff(auth.issueToken(staff), email);
    }

    private LoginResponse citizenLogin(String email, String previousReporterToken) {
        return LoginResponse.citizen(citizens.signIn(email, reporterOf(previousReporterToken)), email);
    }

    private Optional<UUID> reporterOf(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(reporterJwt.decoder().decode(token).getSubject()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private InvalidLoginCodeException failure() {
        log.warn("Código de acesso recusado");
        login("failure");
        return new InvalidLoginCodeException();
    }

    private String hash(String email, String code) {
        return Crypto.hmacHex(codeKey, email + ":" + code);
    }

    private void codeEvent(String result) {
        meters.counter("spbemestar.auth.codes", "result", result).increment();
    }

    private void login(String result) {
        meters.counter("spbemestar.auth.logins", "result", result).increment();
    }

    private Instant now() {
        return Instant.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }
}
