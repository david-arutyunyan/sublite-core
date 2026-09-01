package com.sublite.subscription.domain;

import java.time.Instant;

/**
 * The outer switch is over SubscriptionState, which is sealed with 5
 * permitted records - the compiler rejects this method if a 6th one is
 * ever added and not handled here. The inner switches are over
 * SubscriptionEvent and intentionally use "default -> throw": not every
 * event makes sense in every state, and that's the actual business rule
 * this class exists to enforce.
 */
public final class SubscriptionTransitions {

    private SubscriptionTransitions() {
    }

    public static SubscriptionState apply(SubscriptionState state, SubscriptionEvent event, Instant now) {
        return switch (state) {
            case SubscriptionState.Trial trial -> switch (event) {
                case SubscriptionEvent.ChargeSucceeded e -> new SubscriptionState.Active(now, e.newPeriodEnd());
                case SubscriptionEvent.ChargeFailed e -> new SubscriptionState.Cancelled(now, "TRIAL_CONVERSION_FAILED");
                case SubscriptionEvent.CancelRequested e -> new SubscriptionState.Cancelled(now, e.reason());
                default -> throw new InvalidSubscriptionTransitionException(state, event);
            };

            case SubscriptionState.Active active -> switch (event) {
                case SubscriptionEvent.ChargeSucceeded e -> new SubscriptionState.Active(now, e.newPeriodEnd());
                case SubscriptionEvent.ChargeFailed e -> new SubscriptionState.GracePeriod(active.currentPeriodEnd(), 1);
                case SubscriptionEvent.PauseRequested e -> new SubscriptionState.Paused(active.currentPeriodEnd());
                case SubscriptionEvent.CancelRequested e -> new SubscriptionState.Cancelled(now, e.reason());
                default -> throw new InvalidSubscriptionTransitionException(state, event);
            };

            case SubscriptionState.GracePeriod gracePeriod -> switch (event) {
                case SubscriptionEvent.ChargeSucceeded e -> new SubscriptionState.Active(now, e.newPeriodEnd());
                case SubscriptionEvent.ChargeFailed e ->
                        new SubscriptionState.GracePeriod(gracePeriod.currentPeriodEnd(), gracePeriod.failedAttempts() + 1);
                case SubscriptionEvent.GracePeriodExpired e -> new SubscriptionState.Cancelled(now, "PAYMENT_FAILED");
                case SubscriptionEvent.CancelRequested e -> new SubscriptionState.Cancelled(now, e.reason());
                default -> throw new InvalidSubscriptionTransitionException(state, event);
            };

            case SubscriptionState.Paused paused -> switch (event) {
                case SubscriptionEvent.ResumeRequested e -> new SubscriptionState.Active(now, paused.currentPeriodEnd());
                case SubscriptionEvent.CancelRequested e -> new SubscriptionState.Cancelled(now, e.reason());
                default -> throw new InvalidSubscriptionTransitionException(state, event);
            };

            case SubscriptionState.Cancelled cancelled -> throw new InvalidSubscriptionTransitionException(state, event);
        };
    }
}
