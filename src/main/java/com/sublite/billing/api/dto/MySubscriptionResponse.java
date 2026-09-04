package com.sublite.billing.api.dto;

import com.sublite.subscription.domain.BillingPeriod;
import com.sublite.subscription.domain.Subscription;
import com.sublite.subscription.domain.SubscriptionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Carries currentPeriodStart/End and trialEndsAt as plain fields rather
 * than something pre-rendered - the frontend's "when did this start, when
 * does it end" timeline (see the day-12 planning discussion) needs the raw
 * instants to draw its own progress bar, not a server-side opinion about
 * how to visualize them.
 */
public record MySubscriptionResponse(
        UUID id,
        String planCode,
        String planName,
        BillingPeriod billingPeriod,
        BigDecimal amount,
        String currency,
        SubscriptionStatus status,
        Instant trialEndsAt,
        Instant currentPeriodStart,
        Instant currentPeriodEnd,
        boolean cancelAtPeriodEnd,
        Instant cancelledAt
) {
    public static MySubscriptionResponse from(Subscription subscription) {
        var planPrice = subscription.getPlanPrice();
        var plan = planPrice.getPlan();
        return new MySubscriptionResponse(
                subscription.getId(),
                plan.getCode(),
                plan.getName(),
                planPrice.getBillingPeriod(),
                planPrice.getPrice().amount(),
                planPrice.getPrice().currency(),
                subscription.getStatus(),
                subscription.getTrialEndsAt(),
                subscription.getCurrentPeriodStart(),
                subscription.getCurrentPeriodEnd(),
                subscription.isCancelAtPeriodEnd(),
                subscription.getCancelledAt()
        );
    }
}
