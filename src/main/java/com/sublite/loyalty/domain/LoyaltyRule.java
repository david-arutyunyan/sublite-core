package com.sublite.loyalty.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "loyalty_rules", schema = "loyalty")
public class LoyaltyRule {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private LoyaltyEventType eventType;

    @Column(nullable = false)
    private int points;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LoyaltyRule() {
        // JPA
    }

    public LoyaltyRule(UUID id, LoyaltyEventType eventType, int points, Instant createdAt) {
        this.id = id;
        this.eventType = eventType;
        this.points = points;
        this.active = true;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public LoyaltyEventType getEventType() {
        return eventType;
    }

    public int getPoints() {
        return points;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
