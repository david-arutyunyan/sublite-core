package com.sublite.subscription.domain;

public class InvalidSubscriptionTransitionException extends RuntimeException {

    public InvalidSubscriptionTransitionException(SubscriptionState state, SubscriptionEvent event) {
        super("Cannot apply %s to a subscription in state %s"
                .formatted(event.getClass().getSimpleName(), state.getClass().getSimpleName()));
    }
}
