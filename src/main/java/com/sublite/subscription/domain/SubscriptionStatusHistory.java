package com.sublite.subscription.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * subscriptionId is a plain UUID rather than a @ManyToOne to Subscription:
 * this entity is write-once (one insert per transition) and never navigated
 * from history back to its parent, so a JPA relation would only add an
 * unused lazy-loading path. The "metadata JSONB" column from the migration
 * isn't mapped yet - nothing needs to read/write it before retention (day
 * 7-8) wants to attach offer details to a transition.
 */
@Entity
@Table(name = "subscription_status_history", schema = "subscription")
public class SubscriptionStatusHistory {

    @Id
    private UUID id;

    @Column(name = "subscription_id", nullable = false)
    private UUID subscriptionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private SubscriptionStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private SubscriptionStatus toStatus;

    @Column(length = 50)
    private String reason;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected SubscriptionStatusHistory() {
        // JPA
    }

    public SubscriptionStatusHistory(
            UUID id,
            UUID subscriptionId,
            SubscriptionStatus fromStatus,
            SubscriptionStatus toStatus,
            String reason,
            Instant occurredAt
    ) {
        this.id = id;
        this.subscriptionId = subscriptionId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.reason = reason;
        this.occurredAt = occurredAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSubscriptionId() {
        return subscriptionId;
    }

    public SubscriptionStatus getFromStatus() {
        return fromStatus;
    }

    public SubscriptionStatus getToStatus() {
        return toStatus;
    }

    public String getReason() {
        return reason;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
