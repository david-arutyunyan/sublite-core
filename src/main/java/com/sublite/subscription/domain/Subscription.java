package com.sublite.subscription.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/**
 * customerId is a plain UUID, not a @ManyToOne to shared.domain.User: the
 * subscription module only ever treats another module's aggregate as an
 * opaque id, never loads it directly through JPA. plan/planPrice below ARE
 * real @ManyToOne relations because Plan/PlanPrice live in this same module.
 */
@Entity
@Table(name = "subscriptions", schema = "subscription")
public class Subscription {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_price_id", nullable = false)
    private PlanPrice planPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubscriptionStatus status;

    @Column(name = "trial_ends_at")
    private Instant trialEndsAt;

    @Column(name = "current_period_start", nullable = false)
    private Instant currentPeriodStart;

    @Column(name = "current_period_end", nullable = false)
    private Instant currentPeriodEnd;

    @Column(name = "cancel_at_period_end", nullable = false)
    private boolean cancelAtPeriodEnd;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "failed_charge_attempts", nullable = false)
    private int failedChargeAttempts;

    @Column(name = "cancellation_reason", length = 50)
    private String cancellationReason;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Subscription() {
        // JPA
    }

    public Subscription(
            UUID id,
            UUID customerId,
            PlanPrice planPrice,
            SubscriptionStatus status,
            Instant trialEndsAt,
            Instant currentPeriodStart,
            Instant currentPeriodEnd,
            Instant createdAt
    ) {
        this.id = id;
        this.customerId = customerId;
        this.planPrice = planPrice;
        this.status = status;
        this.trialEndsAt = trialEndsAt;
        this.currentPeriodStart = currentPeriodStart;
        this.currentPeriodEnd = currentPeriodEnd;
        this.cancelAtPeriodEnd = false;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public PlanPrice getPlanPrice() {
        return planPrice;
    }

    public SubscriptionStatus getStatus() {
        return status;
    }

    public Instant getTrialEndsAt() {
        return trialEndsAt;
    }

    public Instant getCurrentPeriodStart() {
        return currentPeriodStart;
    }

    public Instant getCurrentPeriodEnd() {
        return currentPeriodEnd;
    }

    public boolean isCancelAtPeriodEnd() {
        return cancelAtPeriodEnd;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public long getVersion() {
        return version;
    }

    public int getFailedChargeAttempts() {
        return failedChargeAttempts;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    /**
     * Builds the rich state representation from the flat columns above.
     * The table stays one row per subscription regardless of status -
     * SubscriptionState only exists transiently, for SubscriptionTransitions
     * to reason about.
     */
    public SubscriptionState toState() {
        return switch (status) {
            case TRIAL -> new SubscriptionState.Trial(trialEndsAt);
            case ACTIVE -> new SubscriptionState.Active(currentPeriodEnd);
            case GRACE_PERIOD -> new SubscriptionState.GracePeriod(currentPeriodEnd, failedChargeAttempts);
            case PAUSED -> new SubscriptionState.Paused(currentPeriodEnd);
            case CANCELLED -> new SubscriptionState.Cancelled(cancelledAt, cancellationReason);
        };
    }

    /**
     * The reverse of toState(): writes a computed SubscriptionState back
     * onto the flat columns. Exhaustive over the sealed SubscriptionState,
     * so a new state variant fails the build here until handled.
     */
    public void applyState(SubscriptionState newState, Instant now) {
        switch (newState) {
            case SubscriptionState.Trial trial -> {
                this.status = SubscriptionStatus.TRIAL;
                this.trialEndsAt = trial.trialEndsAt();
            }
            case SubscriptionState.Active active -> {
                this.status = SubscriptionStatus.ACTIVE;
                this.currentPeriodEnd = active.currentPeriodEnd();
                this.failedChargeAttempts = 0;
            }
            case SubscriptionState.GracePeriod gracePeriod -> {
                this.status = SubscriptionStatus.GRACE_PERIOD;
                this.currentPeriodEnd = gracePeriod.currentPeriodEnd();
                this.failedChargeAttempts = gracePeriod.failedAttempts();
            }
            case SubscriptionState.Paused paused -> {
                this.status = SubscriptionStatus.PAUSED;
                this.currentPeriodEnd = paused.currentPeriodEnd();
            }
            case SubscriptionState.Cancelled cancelled -> {
                this.status = SubscriptionStatus.CANCELLED;
                this.cancelledAt = cancelled.cancelledAt();
                this.cancellationReason = cancelled.reason();
            }
        }
        this.updatedAt = now;
    }
}
