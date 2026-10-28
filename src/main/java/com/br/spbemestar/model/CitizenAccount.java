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
import java.util.UUID;

/**
 * A conta do cidadão: o e-mail confirmado por código e o id que assina os relatos dele. Os relatos guardam só o
 * HMAC desse id, então a equipe da prefeitura não liga um relato ao e-mail de quem o enviou.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "citizen_account")
public class CitizenAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "reporter_id", nullable = false, unique = true)
    private UUID reporterId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    private CitizenAccount(String email, UUID reporterId, Instant createdAt) {
        this.email = AppUser.normalizeEmail(email);
        this.reporterId = Objects.requireNonNull(reporterId);
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    public static CitizenAccount of(String email, UUID reporterId, Instant createdAt) {
        return new CitizenAccount(email, reporterId, createdAt);
    }

    public void loggedIn(Instant now) {
        this.lastLoginAt = Objects.requireNonNull(now);
    }
}
