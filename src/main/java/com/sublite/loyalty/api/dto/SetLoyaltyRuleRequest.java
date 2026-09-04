package com.sublite.loyalty.api.dto;

import com.sublite.loyalty.domain.LoyaltyEventType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record SetLoyaltyRuleRequest(
        @NotNull LoyaltyEventType eventType,
        @NotNull @Positive Integer points
) {
}
