package com.sublite.billing.application;

import com.sublite.billing.domain.Invoice;
import com.sublite.billing.domain.PaymentAttempt;
import com.sublite.billing.infrastructure.InvoiceRepository;
import com.sublite.loyalty.application.LoyaltyService;
import com.sublite.loyalty.domain.LoyaltyEventType;
import com.sublite.subscription.application.SubscriptionLifecycleService;
import com.sublite.subscription.domain.Subscription;
import com.sublite.subscription.domain.SubscriptionEvent;
import com.sublite.subscription.infrastructure.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * Reaches into other modules only through their public API - subscription's
 * SubscriptionRepository (reads) and SubscriptionLifecycleService (state
 * changes), loyalty's LoyaltyService (awardForEvent) - never through
 * another module's internal domain classes directly. Called loyaltyService
 * directly rather than through a port: unlike retention in day 7-8, the
 * loyalty module actually exists now, so there's no need to decouple from
 * an implementation that doesn't exist yet.
 */
@Service
public class BillingOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(BillingOrchestrator.class);

    private final SubscriptionRepository subscriptions;
    private final InvoiceRepository invoices;
    private final BillingService billingService;
    private final SubscriptionLifecycleService lifecycle;
    private final RetryPolicy retryPolicy;
    private final LoyaltyService loyaltyService;
    private final Clock clock;

    public BillingOrchestrator(
            SubscriptionRepository subscriptions,
            InvoiceRepository invoices,
            BillingService billingService,
            SubscriptionLifecycleService lifecycle,
            RetryPolicy retryPolicy,
            LoyaltyService loyaltyService,
            Clock clock
    ) {
        this.subscriptions = subscriptions;
        this.invoices = invoices;
        this.billingService = billingService;
        this.lifecycle = lifecycle;
        this.retryPolicy = retryPolicy;
        this.loyaltyService = loyaltyService;
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
            log.info("Charge succeeded: subscriptionId={}, invoiceId={}, newPeriodEnd={}", subscriptionId, invoice.getId(), newPeriodEnd);

            // Its own step, after the renewal already committed: if awarding
            // points fails, the subscription stays renewed regardless -
            // a loyalty hiccup should never be able to undo a real payment.
            loyaltyService.awardForEvent(subscription.getCustomerId(), LoyaltyEventType.PAYMENT_SUCCESS);
        } else if (retryPolicy.attemptsExhausted(subscription.getFailedChargeAttempts())) {
            log.warn("Charge failed and retries exhausted, entering grace period: subscriptionId={}, invoiceId={}, failedAttempts={}",
                    subscriptionId, invoice.getId(), subscription.getFailedChargeAttempts());
            lifecycle.handle(subscriptionId, new SubscriptionEvent.GracePeriodExpired());
        } else {
            log.warn("Charge failed, will retry: subscriptionId={}, invoiceId={}, failedAttempts={}",
                    subscriptionId, invoice.getId(), subscription.getFailedChargeAttempts());
            lifecycle.handle(subscriptionId, new SubscriptionEvent.ChargeFailed());
        }
    }
}
