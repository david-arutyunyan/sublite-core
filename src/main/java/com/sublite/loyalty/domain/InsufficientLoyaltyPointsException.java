package com.sublite.loyalty.domain;

import java.util.UUID;

public class InsufficientLoyaltyPointsException extends RuntimeException {

    public InsufficientLoyaltyPointsException(UUID customerId, int requested, int available) {
        super("Customer %s requested to redeem %d points but only has %d".formatted(customerId, requested, available));
    }
}
