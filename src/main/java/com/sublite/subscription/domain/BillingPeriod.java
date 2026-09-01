package com.sublite.subscription.domain;

import java.time.Duration;

public enum BillingPeriod {
    MONTHLY,
    YEARLY;

    /**
     * A fixed Duration is an approximation - calendar months aren't all
     * the same length. Exact calendar arithmetic would mean converting
     * Instant to ZonedDateTime and adding a Period instead; not worth the
     * extra complexity for this project, but worth knowing it's a
     * simplification.
     */
    public Duration approximateDuration() {
        return switch (this) {
            case MONTHLY -> Duration.ofDays(30);
            case YEARLY -> Duration.ofDays(365);
        };
    }
}
