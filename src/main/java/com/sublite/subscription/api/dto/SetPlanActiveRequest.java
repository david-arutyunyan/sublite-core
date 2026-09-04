package com.sublite.subscription.api.dto;

import jakarta.validation.constraints.NotNull;

public record SetPlanActiveRequest(@NotNull Boolean active) {
}
