package com.sublite.subscription.api.dto;

import com.sublite.subscription.domain.BillingPeriod;
import io.swagger.v3.oas.annotations.media.Schema;
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
        @NotBlank @Size(max = 50) @Schema(example = "plus-monthly") String code,
        @NotBlank @Schema(example = "Sublite Plus") String name,
        @Schema(example = "Ad-free streaming with offline downloads") String description,
        @NotNull BillingPeriod billingPeriod,
        @NotNull @DecimalMin(value = "0", inclusive = true) @Schema(example = "9.99") BigDecimal amount,
        @NotBlank @Size(min = 3, max = 3) @Schema(example = "USD") String currency
) {
}
