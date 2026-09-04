package com.sublite.subscription.domain;

import java.util.UUID;

public class NoActiveSubscriptionException extends RuntimeException {

    public NoActiveSubscriptionException(UUID customerId) {
        super("Customer " + customerId + " has no active subscription");
    }
}
