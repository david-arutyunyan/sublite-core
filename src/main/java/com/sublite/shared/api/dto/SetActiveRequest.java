package com.sublite.shared.api.dto;

import jakarta.validation.constraints.NotNull;

/**
 * The same {active} shape every admin "toggle this on/off" endpoint needs
 * (plans, retention steps, ...) - shared once there were two identical
 * single-field DTOs rather than duplicated per module.
 */
public record SetActiveRequest(@NotNull Boolean active) {
}
