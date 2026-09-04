package com.sublite.retention.domain;

import java.util.UUID;

public class RetentionStepNotFoundException extends RuntimeException {

    public RetentionStepNotFoundException(UUID stepId) {
        super("No retention step with id " + stepId);
    }
}
