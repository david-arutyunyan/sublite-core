package com.sublite.retention.api.dto;

import com.sublite.retention.domain.RetentionStepType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * offerId is required only when type == OFFER - enforced in
 * RetentionAdminService.createStep(), not here, since it's a cross-field
 * rule (depends on the value of another field) rather than something a
 * single-field bean-validation annotation expresses cleanly.
 */
public record CreateRetentionStepRequest(
        @NotNull @Min(1) @Schema(example = "2") Integer stepOrder,
        @NotNull RetentionStepType type,
        @Schema(description = "Required when type is OFFER, must be omitted otherwise") UUID offerId
) {
}
