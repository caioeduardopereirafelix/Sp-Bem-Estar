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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Tokens emitidos antes disso não valem mais: o usuário foi desativado. */
    @Column(name = "tokens_valid_after")
    private Instant tokensValidAfter;

    private AppUser(String email, String name, UserRole role, Instant createdAt) {
        this.email = normalizeEmail(email);
        this.name = Objects.requireNonNull(name);
        this.role = Objects.requireNonNull(role);
        this.active = true;
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    public static AppUser of(String email, String name, UserRole role, Instant createdAt) {
        return new AppUser(email, name, role, createdAt);
    }

    /** Desativar corta o acesso na hora: o token que a pessoa já tem deixa de valer. */
    public void deactivate(Instant now) {
        this.active = false;
        this.tokensValidAfter = Objects.requireNonNull(now);
    }

    public void activate() {
        this.active = true;
    }

    public boolean acceptsTokenIssuedAt(Instant issuedAt) {
        return active && (tokensValidAfter == null || (issuedAt != null && !issuedAt.isBefore(tokensValidAfter)));
    }

    public static String normalizeEmail(String email) {
        return Objects.requireNonNull(email).strip().toLowerCase(Locale.ROOT);
    }
}
