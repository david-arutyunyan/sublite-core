package com.sublite.billing.application;

import com.sublite.billing.domain.Invoice;
import com.sublite.billing.domain.PaymentAttempt;
import com.sublite.billing.infrastructure.InvoiceRepository;
import com.sublite.subscription.application.SubscriptionLifecycleService;
import com.sublite.subscription.domain.Subscription;
import com.sublite.subscription.domain.SubscriptionEvent;
import com.sublite.subscription.infrastructure.SubscriptionRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * One subscription's billing, as a sequence of separately-committed steps,
 * NOT one big transaction - deliberately. billingService.chargeInvoice()
 * inserts the payment attempt in its own REQUIRES_NEW transaction (see
 * PaymentAttemptWriter), which runs on its own connection and can't see
 * an invoice this method created but hasn't committed yet; wrapping
 * everything here in one @Transactional caused exactly that - a foreign
 * key violation, because the invoice was still uncommitted when the
 * REQUIRES_NEW insert ran. Each step below commits on its own instead
 * (Spring Data repository calls are transactional per-call by default),
 * which also happens to be the right shape for a billing workflow: a
 * crash between steps is recoverable (the next scheduler run finds the
 * already-created invoice and retries the charge) rather than losing
 * everything to one rolled-back transaction.
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

    public void processOne(UUID subscriptionId) {
        Subscription subscription = subscriptions.findByIdWithPlanPrice(subscriptionId)
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
        subscriptions.save(subscription);

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
