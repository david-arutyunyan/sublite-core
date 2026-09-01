package com.sublite.billing.application;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Pure calculation over plain values, not the Subscription entity - this
 * module needs exactly two facts (how many times has it failed, when was
 * the last try) and nothing else, so it doesn't need to know the entity's
 * shape at all. That also makes it trivial to unit test.
 */
@Component
public class RetryPolicy {

    private final BillingRetryProperties properties;

    public RetryPolicy(BillingRetryProperties properties) {
        this.properties = properties;
    }

    /**
     * Called BEFORE this attempt's failure has been recorded, to decide
     * whether it should send ChargeFailed (retry later) or
     * GracePeriodExpired (give up) - so this checks whether the failure
     * about to happen would be the maxAttempts-th one, not whether it
     * already was.
     */
    public boolean attemptsExhausted(int failedChargeAttempts) {
        return failedChargeAttempts + 1 >= properties.maxAttempts();
    }

    /**
     * Empty means "due now" (never attempted yet). Otherwise the wait
     * doubles with each failed attempt: baseInterval * 2^(attempts - 1).
     */
    public Optional<Instant> nextAttemptAt(int failedChargeAttempts, Instant lastChargeAttemptAt) {
        if (lastChargeAttemptAt == null) {
            return Optional.empty();
        }

        int attempts = Math.max(failedChargeAttempts, 1);
        Duration backoff = properties.baseInterval().multipliedBy(1L << (attempts - 1));
        return Optional.of(lastChargeAttemptAt.plus(backoff));
    }
}
