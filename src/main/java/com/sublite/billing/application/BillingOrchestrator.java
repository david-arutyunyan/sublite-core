package com.sublite.billing.application;

import com.sublite.billing.domain.Invoice;
import com.sublite.billing.domain.PaymentAttempt;
import com.sublite.billing.infrastructure.InvoiceRepository;
import com.sublite.subscription.application.SubscriptionLifecycleService;
import com.sublite.subscription.domain.Subscription;
import com.sublite.subscription.domain.SubscriptionEvent;
import com.sublite.subscription.infrastructure.SubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * One subscription's billing, start to finish, in one transaction. Called
 * per-subscription from BillingScheduler rather than looping inside a
 * single @Transactional method, so one subscription's failure can't roll
 * back everyone else's, and so this method's @Transactional actually takes
 * effect - calling it from a method in the SAME class (self-invocation)
 * would silently skip the proxy and run without a transaction at all,
 * which is why the loop lives in BillingScheduler instead.
 *
 * Reaches into the subscription module only through its public API
 * (SubscriptionRepository for reads, SubscriptionLifecycleService for state
 * changes) - never through SubscriptionTransitions/SubscriptionState directly.
 */
@Service
public class BillingOrchestrator {

    private final SubscriptionRepository subscriptions;
    private final InvoiceRepository invoices;
    private final BillingService billingService;
    private final SubscriptionLifecycleService lifecycle;
    private final RetryPolicy retryPolicy;
    private final Clock clock;

    public BillingOrchestrator(
            SubscriptionRepository subscriptions,
            InvoiceRepository invoices,
            BillingService billingService,
            SubscriptionLifecycleService lifecycle,
            RetryPolicy retryPolicy,
            Clock clock
    ) {
        this.subscriptions = subscriptions;
        this.invoices = invoices;
        this.billingService = billingService;
        this.lifecycle = lifecycle;
        this.retryPolicy = retryPolicy;
        this.clock = clock;
    }

    @Transactional
    public void processOne(UUID subscriptionId) {
        Subscription subscription = subscriptions.findById(subscriptionId)
                .orElseThrow(() -> new NoSuchElementException("Subscription not found: " + subscriptionId));
        Instant now = clock.instant();

        Invoice invoice = invoices.findBySubscriptionIdAndPeriodStart(subscriptionId, subscription.getCurrentPeriodStart())
                .orElseGet(() -> invoices.save(new Invoice(
                        UUID.randomUUID(),
                        subscriptionId,
                        subscription.getCurrentPeriodStart(),
                        subscription.getCurrentPeriodEnd(),
                        subscription.getPlanPrice().getPrice(),
                        now
                )));

        PaymentAttempt attempt = billingService.chargeInvoice(invoice.getId(), UUID.randomUUID());
        subscription.recordChargeAttempt(now);

        if (attempt.succeeded()) {
            Instant newPeriodEnd = now.plus(subscription.getPlanPrice().getBillingPeriod().approximateDuration());
            lifecycle.handle(subscriptionId, new SubscriptionEvent.ChargeSucceeded(newPeriodEnd));
        } else if (retryPolicy.attemptsExhausted(subscription.getFailedChargeAttempts())) {
            lifecycle.handle(subscriptionId, new SubscriptionEvent.GracePeriodExpired());
        } else {
            lifecycle.handle(subscriptionId, new SubscriptionEvent.ChargeFailed());
        }
    }
}
