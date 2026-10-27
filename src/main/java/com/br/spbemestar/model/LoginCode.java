package com.br.spbemestar.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

/** Um código de acesso enviado por e-mail. Vale uma vez, por pouco tempo e por poucas tentativas. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "login_code")
public class LoginCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    private LoginCode(String email, String codeHash, Instant createdAt, Instant expiresAt) {
        this.email = Objects.requireNonNull(email);
        this.codeHash = Objects.requireNonNull(codeHash);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.expiresAt = Objects.requireNonNull(expiresAt);
    }

    public static LoginCode of(String email, String codeHash, Instant createdAt, Instant expiresAt) {
        return new LoginCode(email, codeHash, createdAt, expiresAt);
    }

    public boolean isUsable(Instant now, int maxAttempts) {
        return consumedAt == null && now.isBefore(expiresAt) && attempts < maxAttempts;
    }

    /** Uma tentativa errada; na última, o código deixa de valer e a pessoa precisa pedir outro. */
    public void failedAttempt(Instant now, int maxAttempts) {
        attempts++;
        if (attempts >= maxAttempts) {
            consumedAt = now;
        }
    }

    public void consume(Instant now) {
        this.consumedAt = Objects.requireNonNull(now);
    }
}
