package com.br.spbemestar.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Getter
@NoArgsConstructor
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

    /** {@code traceparent} W3C de quem criou o evento; a publicação continua esse trace. */
    @Column(name = "trace_parent")
    private String traceParent;

    private OutboxEvent(Long aggregateId, String type, String payload, Instant createdAt, String traceParent) {
        this.id = UUID.randomUUID();
        this.aggregateId = Objects.requireNonNull(aggregateId);
        this.type = Objects.requireNonNull(type);
        this.payload = Objects.requireNonNull(payload);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.traceParent = traceParent;
    }

    public static OutboxEvent of(Long aggregateId, String type, String payload, Instant createdAt,
                                 String traceParent) {
        return new OutboxEvent(aggregateId, type, payload, createdAt, traceParent);
    }

    public void markPublished(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public boolean isPublished() {
        return publishedAt != null;
    }
}
