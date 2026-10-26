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

/** Um navegador inscrito para receber os avisos dos relatos de um cidadão. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "push_subscription")
public class PushSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reporter_hash", nullable = false)
    private String reporterHash;

    @Column(nullable = false, unique = true)
    private String endpoint;

    @Column(nullable = false)
    private String p256dh;

    @Column(nullable = false)
    private String auth;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private PushSubscription(String reporterHash, String endpoint, String p256dh, String auth, Instant now) {
        this.endpoint = Objects.requireNonNull(endpoint);
        this.createdAt = Objects.requireNonNull(now);
        update(reporterHash, p256dh, auth, now);
    }

    public static PushSubscription of(String reporterHash, String endpoint, String p256dh, String auth, Instant now) {
        return new PushSubscription(reporterHash, endpoint, p256dh, auth, now);
    }

    /**
     * O mesmo navegador pode se inscrever de novo com outra identidade (o refresh token venceu e a API emitiu
     * outra) ou com chaves novas: o endpoint passa a avisar sobre os relatos da identidade atual.
     */
    public void update(String reporterHash, String p256dh, String auth, Instant now) {
        this.reporterHash = Objects.requireNonNull(reporterHash);
        this.p256dh = Objects.requireNonNull(p256dh);
        this.auth = Objects.requireNonNull(auth);
        this.updatedAt = Objects.requireNonNull(now);
    }
}
