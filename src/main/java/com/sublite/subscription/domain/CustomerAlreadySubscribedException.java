package com.sublite.subscription.domain;

import java.util.UUID;

/**
 * "Already subscribed" covers TRIAL/ACTIVE/GRACE_PERIOD/PAUSED - anything
 * that isn't CANCELLED - matching the DB's own rule (V5's partial unique
 * index, uq_subscriptions_customer_active): one live subscription per
 * customer at a time.
 */
public class CustomerAlreadySubscribedException extends RuntimeException {

    public CustomerAlreadySubscribedException(UUID customerId) {
        super("Customer " + customerId + " already has an active subscription");
    }
}
