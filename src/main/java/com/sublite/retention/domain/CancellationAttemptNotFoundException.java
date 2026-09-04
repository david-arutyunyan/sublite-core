package com.sublite.retention.domain;

import java.util.UUID;

/**
 * Same "not found, whether it doesn't exist or isn't yours" reasoning as
 * SubscriptionNotFoundException - see RetentionCustomerService.
 */
public class CancellationAttemptNotFoundException extends RuntimeException {

    public CancellationAttemptNotFoundException(UUID attemptId) {
        super("No cancellation attempt with id " + attemptId);
    }
}
