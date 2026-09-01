package com.sublite.subscription.application;

import com.sublite.shared.domain.Money;
import com.sublite.shared.domain.User;
import com.sublite.shared.infrastructure.UserRepository;
import com.sublite.subscription.domain.BillingPeriod;
import com.sublite.subscription.domain.Plan;
import com.sublite.subscription.domain.PlanPrice;
import com.sublite.subscription.domain.Subscription;
import com.sublite.subscription.domain.SubscriptionEvent;
import com.sublite.subscription.domain.SubscriptionStatus;
import com.sublite.subscription.infrastructure.PlanPriceRepository;
import com.sublite.subscription.infrastructure.PlanRepository;
import com.sublite.subscription.infrastructure.SubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two threads both try to transition the SAME subscription row at once.
 * Without @Version, both reads would see the old row, both writes would
 * succeed, and whichever wrote last would silently overwrite the other -
 * a lost update. With @Version, the second UPDATE's "WHERE version = ?"
 * matches zero rows once the first UPDATE has bumped it, so Hibernate
 * raises a conflict instead of silently losing a write. The CyclicBarrier
 * makes both threads call handle() at (as close to) the same instant as
 * possible, so their reads race instead of running strictly one-after-another.
 */
@SpringBootTest
@Testcontainers
class SubscriptionConcurrentUpdateTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Autowired
    private SubscriptionLifecycleService lifecycle;
    @Autowired
    private SubscriptionRepository subscriptions;
    @Autowired
    private PlanRepository plans;
    @Autowired
    private PlanPriceRepository planPrices;
    @Autowired
    private UserRepository users;

    @Test
    void exactlyOneOfTwoConcurrentTransitionsSucceeds() throws Exception {
        UUID subscriptionId = createActiveSubscription();
        CyclicBarrier bothReady = new CyclicBarrier(2);

        Callable<Boolean> pauseAttempt = () -> attempt(bothReady,
                () -> lifecycle.handle(subscriptionId, new SubscriptionEvent.PauseRequested()));
        Callable<Boolean> cancelAttempt = () -> attempt(bothReady,
                () -> lifecycle.handle(subscriptionId, new SubscriptionEvent.CancelRequested("USER_REQUESTED")));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<Boolean>> results = pool.invokeAll(List.of(pauseAttempt, cancelAttempt));
        pool.shutdown();

        long succeeded = 0;
        for (Future<Boolean> result : results) {
            if (result.get()) {
                succeeded++;
            }
        }

        assertThat(succeeded)
                .as("exactly one of the two concurrent transitions should win")
                .isEqualTo(1);

        long version = subscriptions.findById(subscriptionId).orElseThrow().getVersion();
        assertThat(version).isEqualTo(1);
    }

    private boolean attempt(CyclicBarrier bothReady, Runnable transition) throws Exception {
        bothReady.await();
        try {
            transition.run();
            return true;
        } catch (ObjectOptimisticLockingFailureException e) {
            return false;
        }
    }

    private UUID createActiveSubscription() {
        Instant now = Instant.now();

        User user = users.save(new User(UUID.randomUUID(), "test-" + UUID.randomUUID() + "@example.com", now, now));
        Plan plan = plans.save(new Plan(UUID.randomUUID(), "PREMIUM-" + UUID.randomUUID(), "Premium", "desc", now));
        PlanPrice price = planPrices.save(new PlanPrice(
                UUID.randomUUID(), plan, BillingPeriod.MONTHLY, new Money(BigDecimal.valueOf(9.99), "USD"), now
        ));

        Subscription subscription = new Subscription(
                UUID.randomUUID(),
                user.getId(),
                price,
                SubscriptionStatus.ACTIVE,
                null,
                now,
                now.plus(Duration.ofDays(30)),
                now
        );
        return subscriptions.save(subscription).getId();
    }
}
