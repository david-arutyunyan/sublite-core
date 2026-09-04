package com.sublite.loyalty.api.dto;

import com.sublite.loyalty.domain.LoyaltyEventType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record SetLoyaltyRuleRequest(
        @NotNull LoyaltyEventType eventType,
        @NotNull @Positive @Schema(example = "50") Integer points
) {
}
