package com.sublite.security.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank @Email @Schema(example = "admin@sublite.dev") String email,
        @NotBlank @Schema(example = "admin123!") String password
) {
}
