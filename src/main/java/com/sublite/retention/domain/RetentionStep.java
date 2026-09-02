package com.sublite.retention.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "retention_steps", schema = "retention")
public class RetentionStep {

    @Id
    private UUID id;

    @Column(name = "step_order", nullable = false)
    private int stepOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RetentionStepType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "offer_id")
    private RetentionOffer offer;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RetentionStep() {
        // JPA
    }

    public RetentionStep(UUID id, int stepOrder, RetentionStepType type, RetentionOffer offer, Instant createdAt) {
        this.id = id;
        this.stepOrder = stepOrder;
        this.type = type;
        this.offer = offer;
        this.active = true;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public int getStepOrder() {
        return stepOrder;
    }

    public RetentionStepType getType() {
        return type;
    }

    public RetentionOffer getOffer() {
        return offer;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
