package com.sublite.subscription.api.dto;

import com.sublite.subscription.domain.BillingPeriod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record AddPlanPriceRequest(
        @NotNull BillingPeriod billingPeriod,
        @NotNull @DecimalMin(value = "0", inclusive = true) @Schema(example = "12.99") BigDecimal amount,
        @NotBlank @Size(min = 3, max = 3) @Schema(example = "USD") String currency
) {
}
