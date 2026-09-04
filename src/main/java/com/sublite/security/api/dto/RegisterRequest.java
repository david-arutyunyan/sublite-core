package com.sublite.security.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Schema(example = "customer@example.com") String email,
        @NotBlank @Size(min = 8, max = 100) @Schema(example = "correct-horse-battery") String password
) {
}
