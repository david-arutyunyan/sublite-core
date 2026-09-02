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

/**
 * subscriptionId stays a plain UUID even though V17 gives it a real DB
 * foreign key - the FK is about data integrity (never orphan a
 * cancellation record), a JPA relation would be about code coupling
 * (retention reaching into subscription's entity graph). Those are two
 * separate questions; this project answers "yes" to the first and "no" to
 * the second here, same as Subscription.customerId did in the subscription
 * module itself. acceptedOffer, by contrast, IS a real relation - it's the
 * same module.
 */
@Entity
@Table(name = "cancellation_attempts", schema = "retention")
public class CancellationAttempt {

    @Id
    private UUID id;

    @Column(name = "subscription_id", nullable = false)
    private UUID subscriptionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CancellationAttemptStatus status;

    @Column(name = "current_step_order", nullable = false)
    private int currentStepOrder;

    @Column(length = 255)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accepted_offer_id")
    private RetentionOffer acceptedOffer;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected CancellationAttempt() {
        // JPA
    }

    public CancellationAttempt(UUID id, UUID subscriptionId, int firstStepOrder, Instant startedAt) {
        this.id = id;
        this.subscriptionId = subscriptionId;
        this.status = CancellationAttemptStatus.IN_PROGRESS;
        this.currentStepOrder = firstStepOrder;
        this.startedAt = startedAt;
    }

    public void recordReason(String reason, int nextStepOrder) {
        this.reason = reason;
        this.currentStepOrder = nextStepOrder;
    }

    public void advanceTo(int nextStepOrder) {
        this.currentStepOrder = nextStepOrder;
    }

    public void retain(RetentionOffer acceptedOffer, Instant completedAt) {
        this.status = CancellationAttemptStatus.RETAINED;
        this.acceptedOffer = acceptedOffer;
        this.completedAt = completedAt;
    }

    public void cancel(Instant completedAt) {
        this.status = CancellationAttemptStatus.CANCELLED;
        this.completedAt = completedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSubscriptionId() {
        return subscriptionId;
    }

    public CancellationAttemptStatus getStatus() {
        return status;
    }

    public int getCurrentStepOrder() {
        return currentStepOrder;
    }

    public String getReason() {
        return reason;
    }

    public RetentionOffer getAcceptedOffer() {
        return acceptedOffer;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
