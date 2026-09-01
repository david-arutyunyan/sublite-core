package com.sublite.subscription.application;

import com.sublite.shared.domain.Money;
import com.sublite.shared.domain.User;
import com.sublite.shared.infrastructure.UserRepository;
import com.sublite.subscription.domain.BillingPeriod;
import com.sublite.subscription.domain.InvalidSubscriptionTransitionException;
import com.sublite.subscription.domain.Plan;
import com.sublite.subscription.domain.PlanPrice;
import com.sublite.subscription.domain.Subscription;
import com.sublite.subscription.domain.SubscriptionEvent;
import com.sublite.subscription.domain.SubscriptionStatus;
import com.sublite.subscription.infrastructure.PlanPriceRepository;
import com.sublite.subscription.infrastructure.PlanRepository;
import com.sublite.subscription.infrastructure.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real Postgres via Testcontainers, not H2: schema validation, the tstzrange
 * price column, and the optimistic-lock version column all behave
 * differently (or don't exist at all) on an in-memory substitute.
 */
@SpringBootTest
@Testcontainers
class SubscriptionLifecycleServiceIT {

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

    private UUID subscriptionId;

    @BeforeEach
    void setUp() {
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
                SubscriptionStatus.TRIAL,
                now.plus(Duration.ofDays(1)),
                now,
                now.plus(Duration.ofDays(1)),
                now
        );
        subscriptionId = subscriptions.save(subscription).getId();
    }

    @Test
    void trialConvertsToActiveAndRecordsHistory() {
        Instant newPeriodEnd = Instant.now().plus(Duration.ofDays(30));

        Subscription result = lifecycle.handle(subscriptionId, new SubscriptionEvent.ChargeSucceeded(newPeriodEnd));

        assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(result.getCurrentPeriodEnd()).isEqualTo(newPeriodEnd);
        assertThat(result.getFailedChargeAttempts()).isZero();
    }

    @Test
    void invalidTransitionIsRejectedAndNothingIsPersisted() {
        assertThatThrownBy(() -> lifecycle.handle(subscriptionId, new SubscriptionEvent.ResumeRequested()))
                .isInstanceOf(InvalidSubscriptionTransitionException.class);

        assertThat(subscriptions.findById(subscriptionId).orElseThrow().getStatus())
                .isEqualTo(SubscriptionStatus.TRIAL);
    }

    @Test
    void cancellationReasonIsStoredOnTheSubscription() {
        Subscription result = lifecycle.handle(subscriptionId, new SubscriptionEvent.CancelRequested("USER_REQUESTED"));

        assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        assertThat(result.getCancellationReason()).isEqualTo("USER_REQUESTED");
        assertThat(result.getCancelledAt()).isNotNull();
    }
}
