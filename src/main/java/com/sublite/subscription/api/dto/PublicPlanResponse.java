package com.sublite.subscription.api.dto;

import com.sublite.subscription.domain.Plan;

import java.util.List;
import java.util.UUID;

public record PublicPlanResponse(
        UUID id,
        String code,
        String name,
        String description,
        List<PublicPriceResponse> prices
) {
    public static PublicPlanResponse from(Plan plan, List<PublicPriceResponse> prices) {
        return new PublicPlanResponse(plan.getId(), plan.getCode(), plan.getName(), plan.getDescription(), prices);
    }
}
