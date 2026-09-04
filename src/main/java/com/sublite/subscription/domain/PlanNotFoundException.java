package com.sublite.subscription.domain;

import java.util.UUID;

public class PlanNotFoundException extends RuntimeException {

    public PlanNotFoundException(UUID planId) {
        super("No plan with id " + planId);
    }
}
