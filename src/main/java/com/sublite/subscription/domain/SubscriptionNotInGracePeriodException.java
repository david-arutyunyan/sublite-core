package com.sublite.subscription.domain;

import java.util.UUID;

public class SubscriptionNotInGracePeriodException extends RuntimeException {

    public SubscriptionNotInGracePeriodException(UUID subscriptionId) {
        super("Subscription " + subscriptionId + " is not in a grace period, so there's no failed payment to retry");
    }
}
