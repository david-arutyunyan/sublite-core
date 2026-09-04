package com.sublite.retention.api.dto;

import com.sublite.retention.domain.RetentionOfferType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record CreateRetentionOfferRequest(
        @NotBlank @Size(max = 50) @Schema(example = "WINBACK-20") String code,
        @NotNull RetentionOfferType type,
        @NotNull
        @Schema(example = "{\"percent\": 20, \"periods\": 3}",
                description = "Shape depends on type: DISCOUNT_PERCENT wants percent+periods, "
                        + "LOYALTY_POINTS wants points, PAUSE_SUBSCRIPTION needs nothing (see RetentionOffer.java)")
        Map<String, Object> parameters
) {
}
