package com.sublite.subscription.api.dto;

import com.sublite.subscription.domain.BillingPeriod;
import com.sublite.subscription.domain.PlanPrice;

import java.math.BigDecimal;
import java.util.UUID;

public record PublicPriceResponse(
        UUID id,
        BillingPeriod billingPeriod,
        BigDecimal amount,
        String currency
) {
    public static PublicPriceResponse from(PlanPrice price) {
        return new PublicPriceResponse(
                price.getId(),
                price.getBillingPeriod(),
                price.getPrice().amount(),
                price.getPrice().currency()
        );
    }
}
