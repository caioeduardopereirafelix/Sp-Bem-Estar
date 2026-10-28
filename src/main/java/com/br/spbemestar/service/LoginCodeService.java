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
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Login sem senha, para todo mundo: a pessoa informa o e-mail, recebe um código de 6 números e o confirma. Quem
 * entra é decidido pelo e-mail: o cadastrado na equipe pelo administrador entra como funcionário (ou CEO); qualquer
 * outro, como cidadão.
 * <p>
 * O código vale poucos minutos e poucas tentativas, e um novo invalida os anteriores (limites em
 * {@link LoginProperties}).
 */
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
        this.codeKey = deriveKey(SecurityConfig.secretKey(security));
    }

    /**
     * Envia um código para o e-mail. A resposta é a mesma para quem é da equipe e para quem não é: só depois de
     * confirmar o código se sabe quem entrou.
     */
    @Transactional
    public LoginCodeSentResponse requestCode(String rawEmail) {
        String email = AppUser.normalizeEmail(rawEmail);
        Instant now = now();
        Optional<LoginCode> last = codes.findFirstByEmailOrderByCreatedAtDesc(email);
        if (last.isPresent() && last.get().getCreatedAt().plus(properties.resendInterval()).isAfter(now)) {
            codeEvent("throttled");
            long wait = Duration.between(now, last.get().getCreatedAt().plus(properties.resendInterval())).toSeconds() + 1;
            throw new LoginCodeThrottledException("Espere um pouco para pedir outro código.", wait);
        }
        if (codes.countByEmailAndCreatedAtAfter(email, now.minus(Duration.ofHours(1))) >= properties.maxCodesPerHour()) {
            codeEvent("throttled");
            throw new LoginCodeThrottledException("Muitos códigos pedidos para este e-mail. Tente de novo mais tarde.",
                    Duration.ofHours(1).toSeconds());
        }

        String code = "%06d".formatted(RANDOM.nextInt(1_000_000));
        codes.invalidatePending(email, now);
        LoginCode saved = codes.save(LoginCode.of(email, hash(email, code), now, now.plus(properties.codeTtl())));
        // dentro da transação: se o e-mail não sair, o código não fica valendo nem conta no limite
        mail.send(message(email, code, properties.codeTtl()));
        codeEvent("sent");
        return new LoginCodeSentResponse(saved.getExpiresAt(), properties.resendInterval().toSeconds());
    }

    /** Confirma o código e entra; as tentativas erradas ficam gravadas mesmo com a recusa. */
    @Transactional(noRollbackFor = InvalidLoginCodeException.class)
    public LoginResponse verify(String rawEmail, String code, String previousReporterToken) {
        String email = AppUser.normalizeEmail(rawEmail);
        Instant now = now();
        LoginCode pending = codes.findFirstByEmailAndConsumedAtIsNullOrderByCreatedAtDesc(email)
                .filter(c -> c.isUsable(now, properties.maxAttempts()))
                .orElseThrow(() -> failure());
        if (!MessageDigest.isEqual(pending.getCodeHash().getBytes(StandardCharsets.UTF_8),
                hash(email, code).getBytes(StandardCharsets.UTF_8))) {
            pending.failedAttempt(now, properties.maxAttempts());
            throw failure();
        }
        pending.consume(now);

        Optional<AppUser> staff = users.findByEmail(email);
        if (staff.isPresent()) {
            if (!staff.get().isActive()) {
                login("failure");
                throw new StaffAccessDisabledException();
            }
            login("success");
            log.info("Usuário {} ({}) entrou", staff.get().getId(), staff.get().getRole());
            return LoginResponse.staff(auth.issueToken(staff.get()), email);
        }
        LoginResponse citizen = LoginResponse.citizen(citizens.signIn(email, reporterOf(previousReporterToken)), email);
        login("success");
        return citizen;
    }

    @Scheduled(cron = "${app.login.cleanup-cron:0 40 3 * * *}")
    @Transactional
    public int deleteOldCodes() {
        // guarda um dia: é o que o limite por hora e a auditoria de um pico de pedidos precisam
        return codes.deleteExpiredBefore(now().minus(Duration.ofDays(1)));
    }

    private Optional<UUID> reporterOf(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(reporterJwt.decoder().decode(token).getSubject()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty(); // vencido ou estranho: a conta começa com uma identidade nova
        }
    }

    private InvalidLoginCodeException failure() {
        // sem o e-mail no log, como no login com senha de antes: a lista de tentativas seria dado pessoal
        log.warn("Código de acesso recusado");
        login("failure");
        return new InvalidLoginCodeException();
    }

    private static EmailSender.Email message(String email, String code, Duration ttl) {
        String text = """
                Seu código de acesso ao SP Bem-Estar é %s.

                Ele vale por %d minutos. Se não foi você que pediu, ignore este e-mail: sem o código, ninguém entra.
                """.formatted(code, ttl.toMinutes());
        String html = """
                <div style="font-family:system-ui,sans-serif;font-size:16px;color:#1b2130">
                  <p>Seu código de acesso ao <strong>SP Bem-Estar</strong> é</p>
                  <p style="font-size:32px;font-weight:700;letter-spacing:6px;margin:16px 0">%s</p>
                  <p>Ele vale por %d minutos. Se não foi você que pediu, ignore este e-mail: sem o código, ninguém entra.</p>
                </div>
                """.formatted(code, ttl.toMinutes());
        return new EmailSender.Email(email, code + " é o seu código do SP Bem-Estar", text, html);
    }

    private String hash(String email, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(codeKey);
            return HexFormat.of().formatHex(mac.doFinal((email + ":" + code).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 indisponível", e);
        }
    }

    private static SecretKey deriveKey(SecretKey operatorKey) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(operatorKey);
            return new SecretKeySpec(mac.doFinal("spbemestar/login-code/v1".getBytes(StandardCharsets.UTF_8)),
                    "HmacSHA256");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 indisponível", e);
        }
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
