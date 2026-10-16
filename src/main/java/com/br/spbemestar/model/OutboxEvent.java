package com.br.spbemestar.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Evento gravado na mesma transação da mudança de domínio e publicado no broker depois
 * (transactional outbox), evitando perder ou duplicar mensagens.
 */
@Entity
@Table(name = "outbox_event")
public class OutboxEvent {

    @Id
    private UUID id;

    @Column(name = "aggregate_id", nullable = false)
    private Long aggregateId;

    @Column(nullable = false)
    private String type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxEvent() {
    }

    private OutboxEvent(Long aggregateId, String type, String payload, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.aggregateId = Objects.requireNonNull(aggregateId);
        this.type = Objects.requireNonNull(type);
        this.payload = Objects.requireNonNull(payload);
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    public static OutboxEvent of(Long aggregateId, String type, String payload, Instant createdAt) {
        return new OutboxEvent(aggregateId, type, payload, createdAt);
    }

    public void markPublished(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public boolean isPublished() {
        return publishedAt != null;
    }

    public UUID getId() {
        return id;
    }

    public Long getAggregateId() {
        return aggregateId;
    }

    public String getType() {
        return type;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }
}
