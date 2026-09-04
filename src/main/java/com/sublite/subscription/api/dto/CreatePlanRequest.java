package com.sublite.subscription.api.dto;

import com.sublite.subscription.domain.BillingPeriod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Creates the plan and its first price together - see
 * PlanAdminService.createPlan() for why. Versioning a price later (a new
 * billing period, or re-pricing an existing one) goes through
 * POST /admin/plans/{id}/prices instead.
 */
public record CreatePlanRequest(
        @NotBlank @Size(max = 50) String code,
        @NotBlank String name,
        String description,
        @NotNull BillingPeriod billingPeriod,
        @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal amount,
        @NotBlank @Size(min = 3, max = 3) String currency
) {
}
