package com.sublite.subscription.domain;

import java.util.UUID;

/**
 * Thrown both when a subscription genuinely doesn't exist AND when it
 * exists but belongs to someone else - see RetentionCustomerService,
 * where customerId always comes from the JWT, never a path parameter.
 * Returning the same 404 either way is deliberate: telling a caller
 * "that id belongs to someone else" instead of "not found" would confirm
 * the id is valid, which is exactly the IDOR leak this is closing.
 */
public class SubscriptionNotFoundException extends RuntimeException {

    public SubscriptionNotFoundException(UUID subscriptionId) {
        super("No subscription with id " + subscriptionId);
    }
}
