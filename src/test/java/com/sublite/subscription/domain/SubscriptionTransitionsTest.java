package com.sublite.subscription.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubscriptionTransitionsTest {

    private final Instant now = Instant.parse("2026-09-01T00:00:00Z");
    private final Instant periodStart = Instant.parse("2026-08-01T00:00:00Z");
    private final Instant periodEnd = Instant.parse("2026-09-01T00:00:00Z");
    private final Instant nextPeriodEnd = Instant.parse("2026-10-01T00:00:00Z");

    @Test
    void trialConvertsToActiveOnSuccessfulCharge() {
        SubscriptionState result = SubscriptionTransitions.apply(
                new SubscriptionState.Trial(now),
                new SubscriptionEvent.ChargeSucceeded(nextPeriodEnd),
                now
        );

        assertThat(result).isEqualTo(new SubscriptionState.Active(now, nextPeriodEnd));
    }

    @Test
    void trialCancelsOnFailedCharge() {
        SubscriptionState result = SubscriptionTransitions.apply(
                new SubscriptionState.Trial(now),
                new SubscriptionEvent.ChargeFailed(),
                now
        );

        assertThat(result).isEqualTo(new SubscriptionState.Cancelled(now, "TRIAL_CONVERSION_FAILED"));
    }

    @Test
    void activeMovesToGracePeriodOnFailedCharge() {
        SubscriptionState result = SubscriptionTransitions.apply(
                new SubscriptionState.Active(periodStart, periodEnd),
                new SubscriptionEvent.ChargeFailed(),
                now
        );

        assertThat(result).isEqualTo(new SubscriptionState.GracePeriod(periodEnd, 1));
    }

    @Test
    void gracePeriodRecoversOnSuccessfulCharge() {
        SubscriptionState result = SubscriptionTransitions.apply(
                new SubscriptionState.GracePeriod(periodEnd, 2),
                new SubscriptionEvent.ChargeSucceeded(nextPeriodEnd),
                now
        );

        assertThat(result).isEqualTo(new SubscriptionState.Active(now, nextPeriodEnd));
    }

    @Test
    void gracePeriodIncrementsFailedAttemptsOnRepeatedFailure() {
        SubscriptionState result = SubscriptionTransitions.apply(
                new SubscriptionState.GracePeriod(periodEnd, 1),
                new SubscriptionEvent.ChargeFailed(),
                now
        );

        assertThat(result).isEqualTo(new SubscriptionState.GracePeriod(periodEnd, 2));
    }

    @Test
    void gracePeriodExpiresIntoCancelled() {
        SubscriptionState result = SubscriptionTransitions.apply(
                new SubscriptionState.GracePeriod(periodEnd, 3),
                new SubscriptionEvent.GracePeriodExpired(),
                now
        );

        assertThat(result).isEqualTo(new SubscriptionState.Cancelled(now, "PAYMENT_FAILED"));
    }

    @Test
    void activeCanBePausedAndResumedBackToTheSamePeriodEnd() {
        SubscriptionState paused = SubscriptionTransitions.apply(
                new SubscriptionState.Active(periodStart, nextPeriodEnd),
                new SubscriptionEvent.PauseRequested(),
                now
        );
        assertThat(paused).isEqualTo(new SubscriptionState.Paused(nextPeriodEnd));

        SubscriptionState resumed = SubscriptionTransitions.apply(
                paused,
                new SubscriptionEvent.ResumeRequested(),
                now
        );
        assertThat(resumed).isEqualTo(new SubscriptionState.Active(now, nextPeriodEnd));
    }

    @Test
    void activeSubscriptionCanBeCancelledDirectly() {
        SubscriptionState result = SubscriptionTransitions.apply(
                new SubscriptionState.Active(periodStart, periodEnd),
                new SubscriptionEvent.CancelRequested("USER_REQUESTED"),
                now
        );

        assertThat(result).isEqualTo(new SubscriptionState.Cancelled(now, "USER_REQUESTED"));
    }

    @Test
    void cancelledIsTerminal() {
        SubscriptionState cancelled = new SubscriptionState.Cancelled(now, "USER_REQUESTED");

        assertThatThrownBy(() ->
                SubscriptionTransitions.apply(cancelled, new SubscriptionEvent.ResumeRequested(), now)
        ).isInstanceOf(InvalidSubscriptionTransitionException.class);
    }

    @Test
    void pauseIsNotAllowedDuringTrial() {
        assertThatThrownBy(() ->
                SubscriptionTransitions.apply(new SubscriptionState.Trial(now), new SubscriptionEvent.PauseRequested(), now)
        ).isInstanceOf(InvalidSubscriptionTransitionException.class);
    }

    @Test
    void resumeIsNotAllowedWhileActive() {
        assertThatThrownBy(() ->
                SubscriptionTransitions.apply(new SubscriptionState.Active(periodStart, periodEnd), new SubscriptionEvent.ResumeRequested(), now)
        ).isInstanceOf(InvalidSubscriptionTransitionException.class);
    }
}
