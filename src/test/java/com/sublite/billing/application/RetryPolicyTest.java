package com.sublite.billing.application;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RetryPolicyTest {

    private final RetryPolicy policy = new RetryPolicy(new BillingRetryProperties(Duration.ofDays(1), 3));
    private final Instant lastAttempt = Instant.parse("2026-09-01T00:00:00Z");

    @Test
    void neverAttemptedIsDueImmediately() {
        assertThat(policy.nextAttemptAt(0, null)).isEmpty();
    }

    @Test
    void firstRetryWaitsOneBaseInterval() {
        assertThat(policy.nextAttemptAt(1, lastAttempt))
                .contains(lastAttempt.plus(Duration.ofDays(1)));
    }

    @Test
    void secondRetryWaitsTwiceAsLong() {
        assertThat(policy.nextAttemptAt(2, lastAttempt))
                .contains(lastAttempt.plus(Duration.ofDays(2)));
    }

    @Test
    void thirdRetryWaitsFourTimesAsLong() {
        assertThat(policy.nextAttemptAt(3, lastAttempt))
                .contains(lastAttempt.plus(Duration.ofDays(4)));
    }

    @Test
    void attemptsAreNotExhaustedBeforeMaxAttempts() {
        // maxAttempts = 3: this checks whether the NEXT failure (attempts+1)
        // would already be the 3rd one.
        assertThat(policy.attemptsExhausted(0)).isFalse();
        assertThat(policy.attemptsExhausted(1)).isFalse();
    }

    @Test
    void attemptsAreExhaustedOnceNextFailureWouldReachMax() {
        assertThat(policy.attemptsExhausted(2)).isTrue();
    }
}
