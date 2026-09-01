package com.sublite.billing.application;

import com.sublite.subscription.domain.Subscription;
import com.sublite.subscription.domain.SubscriptionStatus;
import com.sublite.subscription.infrastructure.SubscriptionRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * @SchedulerLock is what stops two app instances from both running this at
 * once - without it, a second instance starting mid-cycle would double-bill
 * every due subscription. lockAtLeastFor guards the opposite case: a run
 * that finishes in under 10s doesn't free the lock early enough for another
 * instance to immediately start a redundant second pass.
 *
 * @ConditionalOnProperty is what lets tests turn this off (see
 * src/test/resources/application.yml): @SpringBootTest boots the WHOLE
 * context, @Scheduled included, so without this every test that creates a
 * due subscription would race the real background job for the same row.
 */
@Component
@ConditionalOnProperty(prefix = "sublite.billing.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class BillingScheduler {

    private static final List<SubscriptionStatus> BILLABLE_STATUSES =
            List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.GRACE_PERIOD);

    private final SubscriptionRepository subscriptions;
    private final BillingOrchestrator orchestrator;
    private final RetryPolicy retryPolicy;
    private final Clock clock;

    public BillingScheduler(
            SubscriptionRepository subscriptions,
            BillingOrchestrator orchestrator,
            RetryPolicy retryPolicy,
            Clock clock
    ) {
        this.subscriptions = subscriptions;
        this.orchestrator = orchestrator;
        this.retryPolicy = retryPolicy;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${sublite.billing.scheduler.interval}")
    @SchedulerLock(name = "billing-cycle", lockAtMostFor = "PT5M", lockAtLeastFor = "PT10S")
    public void runBillingCycle() {
        Instant now = clock.instant();

        subscriptions.findByStatusIn(BILLABLE_STATUSES).stream()
                .filter(subscription -> isDue(subscription, now))
                .map(Subscription::getId)
                .forEach(orchestrator::processOne);
    }

    private boolean isDue(Subscription subscription, Instant now) {
        return switch (subscription.getStatus()) {
            case ACTIVE -> !subscription.getCurrentPeriodEnd().isAfter(now);
            case GRACE_PERIOD -> retryPolicy
                    .nextAttemptAt(subscription.getFailedChargeAttempts(), subscription.getLastChargeAttemptAt())
                    .map(nextAttempt -> !nextAttempt.isAfter(now))
                    .orElse(true);
            default -> false;
        };
    }
}
