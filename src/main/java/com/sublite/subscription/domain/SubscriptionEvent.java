package com.sublite.subscription.domain;

import java.time.Instant;

/**
 * Things that can happen to a subscription. Billing (day 5-6) will be the
 * caller producing ChargeSucceeded/ChargeFailed; for now nothing calls this
 * yet except our own tests and SubscriptionLifecycleService.
 */
public sealed interface SubscriptionEvent {

    record ChargeSucceeded(Instant newPeriodEnd) implements SubscriptionEvent {
    }

    record ChargeFailed() implements SubscriptionEvent {
    }

    record GracePeriodExpired() implements SubscriptionEvent {
    }

    record PauseRequested() implements SubscriptionEvent {
    }

    record ResumeRequested() implements SubscriptionEvent {
    }

    record CancelRequested(String reason) implements SubscriptionEvent {
    }
}
