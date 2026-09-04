package com.sublite.subscription.api.dto;

import com.sublite.subscription.domain.BillingPeriod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record AddPlanPriceRequest(
        @NotNull BillingPeriod billingPeriod,
        @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal amount,
        @NotBlank @Size(min = 3, max = 3) String currency
) {
}
