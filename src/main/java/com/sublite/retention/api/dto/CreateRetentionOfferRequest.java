package com.sublite.retention.api.dto;

import com.sublite.retention.domain.RetentionOfferType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record CreateRetentionOfferRequest(
        @NotBlank @Size(max = 50) String code,
        @NotNull RetentionOfferType type,
        @NotNull Map<String, Object> parameters
) {
}
