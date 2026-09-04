package com.sublite.retention.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SubmitReasonRequest(
        @NotBlank @Size(max = 255) @Schema(example = "Too expensive") String reason
) {
}
