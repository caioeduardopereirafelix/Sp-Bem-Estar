package com.br.spbemestar.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String name;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Tokens emitidos antes disso não valem mais: senha trocada ou usuário desativado. */
    @Column(name = "tokens_valid_after")
    private Instant tokensValidAfter;

    private AppUser(String email, String name, String passwordHash, UserRole role, Instant createdAt) {
        this.email = normalizeEmail(email);
        this.name = Objects.requireNonNull(name);
        this.passwordHash = Objects.requireNonNull(passwordHash);
        this.role = Objects.requireNonNull(role);
        this.active = true;
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    public static AppUser of(String email, String name, String passwordHash, UserRole role, Instant createdAt) {
        return new AppUser(email, name, passwordHash, role, createdAt);
    }

    /** Desativar corta o acesso na hora: o token que a pessoa já tem deixa de valer. */
    public void deactivate(Instant now) {
        this.active = false;
        this.tokensValidAfter = Objects.requireNonNull(now);
    }

    public void activate() {
        this.active = true;
    }

    /** Trocar a senha encerra as outras sessões: quem tinha o token antigo (talvez roubado) perde o acesso. */
    public void changePassword(String passwordHash, Instant now) {
        this.passwordHash = Objects.requireNonNull(passwordHash);
        this.tokensValidAfter = Objects.requireNonNull(now);
    }

    public boolean acceptsTokenIssuedAt(Instant issuedAt) {
        return active && (tokensValidAfter == null || (issuedAt != null && !issuedAt.isBefore(tokensValidAfter)));
    }

    public static String normalizeEmail(String email) {
        return Objects.requireNonNull(email).strip().toLowerCase(Locale.ROOT);
    }
}
