package com.sublite.billing.application;

import com.sublite.billing.domain.ChargeResult;
import com.sublite.billing.domain.Invoice;
import com.sublite.billing.domain.InvoiceStatus;
import com.sublite.billing.domain.PaymentGateway;
import com.sublite.billing.infrastructure.InvoiceRepository;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * The payment provider is mocked here (not RandomPaymentGateway) so the
 * test controls the outcome instead of depending on chance - randomness
 * belongs in the running app, not in a test asserting a specific branch.
 *
 * The scheduler is disabled: these fixtures are deliberately "due" for
 * billing so orchestrator.processOne() has something to act on, which
 * would also make them a target for the real background job otherwise.
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "sublite.billing.scheduler.enabled=false")
class BillingOrchestratorIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Autowired
    private BillingOrchestrator orchestrator;
    @Autowired
    private SubscriptionRepository subscriptions;
    @Autowired
    private InvoiceRepository invoices;
    @Autowired
    private PlanRepository plans;
    @Autowired
    private PlanPriceRepository planPrices;
    @Autowired
    private UserRepository users;

    @MockitoBean
    private PaymentGateway paymentGateway;

    @Test
    void successfulChargeRenewsTheSubscriptionAndPaysTheInvoice() {
        when(paymentGateway.charge(any(), any())).thenReturn(new ChargeResult.Success("ref-1"));
        UUID subscriptionId = createDueActiveSubscription();
        Instant beforeCall = Instant.now();

        orchestrator.processOne(subscriptionId);

        Subscription updated = subscriptions.findById(subscriptionId).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(updated.getFailedChargeAttempts()).isZero();
        assertThat(updated.getCurrentPeriodStart()).isAfterOrEqualTo(beforeCall);
        assertThat(updated.getCurrentPeriodEnd()).isAfter(updated.getCurrentPeriodStart());

        List<Invoice> subscriptionInvoices = invoices.findAll().stream()
                .filter(invoice -> invoice.getSubscriptionId().equals(subscriptionId))
                .toList();
        assertThat(subscriptionInvoices).hasSize(1);
        assertThat(subscriptionInvoices.get(0).getStatus()).isEqualTo(InvoiceStatus.PAID);
    }

    @Test
    void declinedChargeMovesTheSubscriptionIntoGracePeriod() {
        when(paymentGateway.charge(any(), any())).thenReturn(new ChargeResult.Declined("INSUFFICIENT_FUNDS"));
        UUID subscriptionId = createDueActiveSubscription();

        orchestrator.processOne(subscriptionId);

        Subscription updated = subscriptions.findById(subscriptionId).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(SubscriptionStatus.GRACE_PERIOD);
        assertThat(updated.getFailedChargeAttempts()).isEqualTo(1);
        assertThat(updated.getLastChargeAttemptAt()).isNotNull();

        List<Invoice> subscriptionInvoices = invoices.findAll().stream()
                .filter(invoice -> invoice.getSubscriptionId().equals(subscriptionId))
                .toList();
        assertThat(subscriptionInvoices).hasSize(1);
        assertThat(subscriptionInvoices.get(0).getStatus()).isEqualTo(InvoiceStatus.PENDING);
    }

    @Test
    void reprocessingTheSameDuePeriodReusesTheSameInvoice() {
        when(paymentGateway.charge(any(), any())).thenReturn(new ChargeResult.Declined("INSUFFICIENT_FUNDS"));
        UUID subscriptionId = createDueActiveSubscription();

        orchestrator.processOne(subscriptionId);
        orchestrator.processOne(subscriptionId);

        List<Invoice> subscriptionInvoices = invoices.findAll().stream()
                .filter(invoice -> invoice.getSubscriptionId().equals(subscriptionId))
                .toList();
        assertThat(subscriptionInvoices).hasSize(1);

        Subscription updated = subscriptions.findById(subscriptionId).orElseThrow();
        assertThat(updated.getFailedChargeAttempts()).isEqualTo(2);
    }

    private UUID createDueActiveSubscription() {
        Instant now = Instant.now();

        User user = users.save(new User(UUID.randomUUID(), "test-" + UUID.randomUUID() + "@example.com", now, now));
        Plan plan = plans.save(new Plan(UUID.randomUUID(), "PREMIUM-" + UUID.randomUUID(), "Premium", "desc", now));
        PlanPrice price = planPrices.save(new PlanPrice(
                UUID.randomUUID(), plan, BillingPeriod.MONTHLY, new Money(BigDecimal.valueOf(9.99), "USD"), now
        ));

        // periodEnd already in the past -> due for billing right now
        Subscription subscription = new Subscription(
                UUID.randomUUID(),
                user.getId(),
                price,
                SubscriptionStatus.ACTIVE,
                null,
                now.minus(Duration.ofDays(30)),
                now.minus(Duration.ofSeconds(1)),
                now.minus(Duration.ofDays(30))
        );
        return subscriptions.save(subscription).getId();
    }
}
