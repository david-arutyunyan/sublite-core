package com.sublite.retention.application;

import com.redis.testcontainers.RedisContainer;
import com.sublite.retention.domain.CancellationAttempt;
import com.sublite.retention.domain.CancellationAttemptStatus;
import com.sublite.retention.domain.InvalidCancellationStepException;
import com.sublite.retention.domain.RetentionOffer;
import com.sublite.retention.domain.RetentionOfferType;
import com.sublite.retention.domain.RetentionStep;
import com.sublite.retention.domain.RetentionStepType;
import com.sublite.retention.infrastructure.CancellationAttemptRepository;
import com.sublite.retention.infrastructure.RetentionOfferRepository;
import com.sublite.retention.infrastructure.RetentionStepRepository;
import com.sublite.shared.domain.Money;
import com.sublite.shared.domain.User;
import com.sublite.shared.infrastructure.UserRepository;
import com.sublite.subscription.domain.BillingPeriod;
import com.sublite.subscription.domain.Plan;
import com.sublite.subscription.domain.PlanPrice;
import com.sublite.subscription.domain.Subscription;
import com.sublite.subscription.domain.SubscriptionStatus;
import com.sublite.subscription.infrastructure.PlanPriceRepository;
import com.sublite.subscription.infrastructure.PlanRepository;
import com.sublite.subscription.infrastructure.SubscriptionRepository;
import org.junit.jupiter.api.AfterEach;
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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Flow used in these tests: SURVEY(1) -> OFFER(2, pause) -> CONFIRMATION(3).
 * Real Postgres AND real Redis via Testcontainers - the cache is part of
 * what's being tested, not something to fake out.
 */
@SpringBootTest
@Testcontainers
class RetentionFlowServiceIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Container
    @ServiceConnection
    static final RedisContainer redis = new RedisContainer("redis:7");

    @Autowired
    private RetentionFlowService flowService;
    @Autowired
    private RetentionFlowConfigService flowConfigService;
    @Autowired
    private RetentionOfferRepository offers;
    @Autowired
    private RetentionStepRepository steps;
    @Autowired
    private CancellationAttemptRepository attempts;
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

        RetentionOffer pauseOffer = offers.save(new RetentionOffer(
                UUID.randomUUID(), "PAUSE-" + UUID.randomUUID(), RetentionOfferType.PAUSE_SUBSCRIPTION, Map.of(), now
        ));
        steps.save(new RetentionStep(UUID.randomUUID(), 1, RetentionStepType.SURVEY, null, now));
        steps.save(new RetentionStep(UUID.randomUUID(), 2, RetentionStepType.OFFER, pauseOffer, now));
        steps.save(new RetentionStep(UUID.randomUUID(), 3, RetentionStepType.CONFIRMATION, null, now));

        User user = users.save(new User(UUID.randomUUID(), "test-" + UUID.randomUUID() + "@example.com", now, now));
        Plan plan = plans.save(new Plan(UUID.randomUUID(), "PREMIUM-" + UUID.randomUUID(), "Premium", "desc", now));
        PlanPrice price = planPrices.save(new PlanPrice(
                UUID.randomUUID(), plan, BillingPeriod.MONTHLY, new Money(BigDecimal.valueOf(9.99), "USD"), now
        ));
        Subscription subscription = new Subscription(
                UUID.randomUUID(), user.getId(), price, SubscriptionStatus.ACTIVE,
                null, now, now.plus(Duration.ofDays(30)), now
        );
        subscriptionId = subscriptions.save(subscription).getId();
    }

    /**
     * The Postgres AND Redis containers are reused across all tests in
     * this class (that's the point of Testcontainers reuse-within-class),
     * so leftover rows/cache from one test would break the next one's
     * setUp() - retention_steps.step_order is fixed 1/2/3, not randomized
     * like the other fixtures' codes, so it collides if not cleaned up.
     */
    @AfterEach
    void tearDown() {
        attempts.deleteAll();
        steps.deleteAll();
        offers.deleteAll();
        flowConfigService.evictCache();
    }

    @Test
    void acceptingThePauseOfferRetainsTheSubscriberAndPausesTheSubscription() {
        CancellationAttempt attempt = flowService.start(subscriptionId);
        flowService.submitReason(attempt.getId(), "too expensive");

        CancellationAttempt result = flowService.acceptCurrentOffer(attempt.getId());

        assertThat(result.getStatus()).isEqualTo(CancellationAttemptStatus.RETAINED);
        assertThat(result.getAcceptedOffer()).isNotNull();
        assertThat(subscriptions.findById(subscriptionId).orElseThrow().getStatus())
                .isEqualTo(SubscriptionStatus.PAUSED);
    }

    @Test
    void decliningTheOfferAndConfirmingCancelsTheSubscription() {
        CancellationAttempt attempt = flowService.start(subscriptionId);
        flowService.submitReason(attempt.getId(), "too expensive");
        flowService.declineCurrentOffer(attempt.getId());

        CancellationAttempt result = flowService.confirmCancellation(attempt.getId());

        assertThat(result.getStatus()).isEqualTo(CancellationAttemptStatus.CANCELLED);
        assertThat(subscriptions.findById(subscriptionId).orElseThrow().getStatus())
                .isEqualTo(SubscriptionStatus.CANCELLED);
    }

    @Test
    void cannotAcceptAnOfferBeforeAnsweringTheSurvey() {
        CancellationAttempt attempt = flowService.start(subscriptionId);

        assertThatThrownBy(() -> flowService.acceptCurrentOffer(attempt.getId()))
                .isInstanceOf(InvalidCancellationStepException.class);
    }

    @Test
    void activeFlowIsServedFromCacheOnTheSecondRead() {
        RetentionFlowConfig first = flowConfigService.getActiveFlow();

        // adding a step directly in Postgres, bypassing the cache - if the
        // second read still returns 3 steps, it came from Redis, not Postgres.
        steps.save(new RetentionStep(UUID.randomUUID(), 4, RetentionStepType.CONFIRMATION, null, Instant.now()));

        RetentionFlowConfig second = flowConfigService.getActiveFlow();

        assertThat(first.steps()).hasSize(3);
        assertThat(second.steps()).hasSize(3);

        flowConfigService.evictCache();
        RetentionFlowConfig afterEviction = flowConfigService.getActiveFlow();
        assertThat(afterEviction.steps()).hasSize(4);
    }
}
