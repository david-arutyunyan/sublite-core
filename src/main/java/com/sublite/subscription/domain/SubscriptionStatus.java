package com.sublite.subscription.domain;

/**
 * Plain enum for now, matching the CHECK constraint in the subscriptions table.
 * Day 3-4 will likely wrap this in a sealed interface per-state (TrialState,
 * GracePeriodState, ...) once each state needs its own data and transition
 * rules — that's where exhaustive switch pattern matching earns its keep.
 */
public enum SubscriptionStatus {
    TRIAL,
    ACTIVE,
    GRACE_PERIOD,
    PAUSED,
    CANCELLED
}
