package com.sublite.billing.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record PurchaseSubscriptionRequest(
        @NotNull @Schema(description = "id from GET /plans's prices[].id") UUID planPriceId
) {
}
