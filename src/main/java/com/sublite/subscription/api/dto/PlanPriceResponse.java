package com.sublite.subscription.api.dto;

import com.sublite.subscription.domain.BillingPeriod;
import com.sublite.subscription.domain.PlanPrice;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PlanPriceResponse(
        UUID id,
        BillingPeriod billingPeriod,
        BigDecimal amount,
        String currency,
        Instant createdAt
) {
    public static PlanPriceResponse from(PlanPrice price) {
        return new PlanPriceResponse(
                price.getId(),
                price.getBillingPeriod(),
                price.getPrice().amount(),
                price.getPrice().currency(),
                price.getCreatedAt()
        );
    }
}
