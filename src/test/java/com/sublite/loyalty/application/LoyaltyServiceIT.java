package com.sublite.loyalty.application;

import com.sublite.loyalty.domain.InsufficientLoyaltyPointsException;
import com.sublite.loyalty.domain.LoyaltyEventType;
import com.sublite.loyalty.domain.LoyaltyRule;
import com.sublite.loyalty.infrastructure.LoyaltyRuleRepository;
import com.sublite.shared.domain.User;
import com.sublite.shared.infrastructure.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class LoyaltyServiceIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Autowired
    private LoyaltyService loyaltyService;
    @Autowired
    private LoyaltyRuleRepository rules;
    @Autowired
    private UserRepository users;

    /**
     * loyalty_rules has a partial unique index on (event_type) WHERE
     * is_active - only one test in this class inserts a rule, but the
     * Postgres container (and its data) is reused across every test method
     * in the class, so a leftover active rule from one test would break
     * awardForEventWithNoActiveRuleDoesNothing's "no rule configured" case.
     */
    @AfterEach
    void tearDown() {
        rules.deleteAll();
    }

    @Test
    void awardCreatesAnAccountOnFirstUseAndCreditsPoints() {
        UUID customerId = newCustomer();

        loyaltyService.award(customerId, 150, "RETENTION_OFFER_ACCEPTED");

        assertThat(loyaltyService.getBalance(customerId)).isEqualTo(150);
    }

    @Test
    void repeatedAwardsAccumulateOnTheSameAccount() {
        UUID customerId = newCustomer();

        loyaltyService.award(customerId, 100, "FIRST");
        loyaltyService.award(customerId, 50, "SECOND");

        assertThat(loyaltyService.getBalance(customerId)).isEqualTo(150);
    }

    @Test
    void awardForEventUsesTheConfiguredRuleAmount() {
        UUID customerId = newCustomer();
        rules.save(new LoyaltyRule(UUID.randomUUID(), LoyaltyEventType.PAYMENT_SUCCESS, 75, Instant.now()));

        loyaltyService.awardForEvent(customerId, LoyaltyEventType.PAYMENT_SUCCESS);

        assertThat(loyaltyService.getBalance(customerId)).isEqualTo(75);
    }

    @Test
    void awardForEventWithNoActiveRuleDoesNothing() {
        UUID customerId = newCustomer();

        loyaltyService.awardForEvent(customerId, LoyaltyEventType.PAYMENT_SUCCESS);

        assertThat(loyaltyService.getBalance(customerId)).isZero();
    }

    @Test
    void redeemDecreasesBalanceWhenSufficient() {
        UUID customerId = newCustomer();
        loyaltyService.award(customerId, 200, "SEED");

        loyaltyService.redeem(customerId, 120, "REDEEMED_FOR_DISCOUNT");

        assertThat(loyaltyService.getBalance(customerId)).isEqualTo(80);
    }

    @Test
    void redeemBeyondBalanceThrowsAndChangesNothing() {
        UUID customerId = newCustomer();
        loyaltyService.award(customerId, 50, "SEED");

        assertThatThrownBy(() -> loyaltyService.redeem(customerId, 51, "REDEEMED_FOR_DISCOUNT"))
                .isInstanceOf(InsufficientLoyaltyPointsException.class);

        assertThat(loyaltyService.getBalance(customerId)).isEqualTo(50);
    }

    /**
     * The specific race this project keeps testing for: two threads award
     * points to the SAME brand-new customer at once, both racing to create
     * the first LoyaltyAccount. Exactly one account should exist afterward,
     * with both awards' points reflected in its balance - not two accounts,
     * and not a lost update.
     */
    @Test
    void concurrentFirstAwardsForTheSameCustomerDoNotCreateDuplicateAccounts() throws Exception {
        UUID customerId = newCustomer();
        CyclicBarrier bothReady = new CyclicBarrier(2);

        Callable<Void> awardTask = () -> {
            bothReady.await();
            loyaltyService.award(customerId, 100, "CONCURRENT_AWARD");
            return null;
        };

        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<Void>> results = pool.invokeAll(List.of(awardTask, awardTask));
        pool.shutdown();
        for (Future<Void> result : results) {
            result.get();
        }

        assertThat(loyaltyService.getBalance(customerId)).isEqualTo(200);
    }

    private UUID newCustomer() {
        Instant now = Instant.now();
        User user = users.save(new User(UUID.randomUUID(), "test-" + UUID.randomUUID() + "@example.com", now, now));
        return user.getId();
    }
}
