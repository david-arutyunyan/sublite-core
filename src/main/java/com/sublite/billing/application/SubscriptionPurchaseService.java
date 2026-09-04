package com.sublite.billing.application;

import com.sublite.subscription.domain.CustomerAlreadySubscribedException;
import com.sublite.subscription.domain.NoActiveSubscriptionException;
import com.sublite.subscription.domain.PlanPrice;
import com.sublite.subscription.domain.PlanPriceNotFoundException;
import com.sublite.subscription.domain.Subscription;
import com.sublite.subscription.domain.SubscriptionStatus;
import com.sublite.subscription.infrastructure.PlanPriceRepository;
import com.sublite.subscription.infrastructure.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Lives in billing, not subscription, even though it answers /subscriptions:
 * purchasing calls into BillingOrchestrator (charges the first invoice),
 * and subscription must never depend on billing - ModuleBoundaryTest
 * (ArchUnit) already enforces billing -> subscription as the only allowed
 * direction, since billing is the module earmarked for extraction into its
 * own service later (see the invoices table's dangling subscription_id).
 * Putting the "buy a subscription" workflow here, next to
 * BillingOrchestrator, which already coordinates subscription + billing +
 * loyalty for renewals, keeps that direction intact instead of adding a
 * second cross-module edge that would create a cycle.
 */
@Service
public class SubscriptionPurchaseService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionPurchaseService.class);

    private static final List<SubscriptionStatus> LIVE_STATUSES = List.of(
            SubscriptionStatus.TRIAL, SubscriptionStatus.ACTIVE, SubscriptionStatus.GRACE_PERIOD, SubscriptionStatus.PAUSED
    );

    private final SubscriptionRepository subscriptions;
    private final PlanPriceRepository planPrices;
    private final BillingOrchestrator billingOrchestrator;
    private final Clock clock;

    public SubscriptionPurchaseService(
            SubscriptionRepository subscriptions,
            PlanPriceRepository planPrices,
            BillingOrchestrator billingOrchestrator,
            Clock clock
    ) {
        this.subscriptions = subscriptions;
        this.planPrices = planPrices;
        this.billingOrchestrator = billingOrchestrator;
        this.clock = clock;
    }

    /**
     * Two separate steps, not one @Transactional method wrapping both -
     * same reasoning as BillingOrchestrator.processOne() itself (see its
     * doc comment): the payment attempt it triggers commits on its own
     * REQUIRES_NEW transaction, which can't see this subscription unless
     * createSubscription() has already committed it first. Simplified on
     * purpose (see the day-12 planning discussion): the subscription goes
     * straight to ACTIVE and gets charged once immediately, no TRIAL state,
     * no separate "await payment" step - if the charge happens to fail
     * (RandomPaymentGateway does fail sometimes), the existing retry/
     * grace-period machinery in BillingOrchestrator takes over exactly as
     * it would for a renewal failure, nothing purchase-specific to build
     * for that case.
     */
    public Subscription purchase(UUID customerId, UUID planPriceId) {
        UUID subscriptionId = createSubscription(customerId, planPriceId);
        log.info("Subscription created: customerId={}, subscriptionId={}, planPriceId={}", customerId, subscriptionId, planPriceId);
        billingOrchestrator.processOne(subscriptionId);
        return subscriptions.findByIdWithPlanPrice(subscriptionId).orElseThrow();
    }

    private UUID createSubscription(UUID customerId, UUID planPriceId) {
        PlanPrice planPrice = planPrices.findById(planPriceId)
                .orElseThrow(() -> new PlanPriceNotFoundException(planPriceId));

        Instant now = Instant.now(clock);
        Instant periodEnd = now.plus(planPrice.getBillingPeriod().approximateDuration());
        Subscription subscription = new Subscription(
                UUID.randomUUID(), customerId, planPrice, SubscriptionStatus.ACTIVE, null, now, periodEnd, now
        );

        // The real guarantee against two concurrent purchases both
        // slipping through is the DB's own uq_subscriptions_customer_active
        // partial unique index, not this method's happens-to-run-first
        // ordering - a plain check-then-insert wouldn't close that race
        // under READ_COMMITTED anyway.
        try {
            return subscriptions.save(subscription).getId();
        } catch (DataIntegrityViolationException alreadySubscribed) {
            throw new CustomerAlreadySubscribedException(customerId);
        }
    }

    public Subscription getMySubscription(UUID customerId) {
        return subscriptions.findByCustomerIdAndStatusInWithPlan(customerId, LIVE_STATUSES)
                .orElseThrow(() -> new NoActiveSubscriptionException(customerId));
    }
}
