package com.sublite.subscription.domain;

import java.time.Instant;

/**
 * One record per status: each carries exactly the data that status needs,
 * instead of one big class with fields that are null in most statuses.
 * Sealed means the compiler knows this list is final - see
 * SubscriptionTransitions for why that matters.
 */
public sealed interface SubscriptionState {

    record Trial(Instant trialEndsAt) implements SubscriptionState {
    }

    record Active(Instant currentPeriodStart, Instant currentPeriodEnd) implements SubscriptionState {
    }

    record GracePeriod(Instant currentPeriodEnd, int failedAttempts) implements SubscriptionState {
    }

    record Paused(Instant currentPeriodEnd) implements SubscriptionState {
    }

    record Cancelled(Instant cancelledAt, String reason) implements SubscriptionState {
    }
}
