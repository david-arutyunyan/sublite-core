package com.sublite.retention.domain;

import java.util.UUID;

/**
 * A port: retention knows it needs to award points for a LOYALTY_POINTS
 * offer, but doesn't know how loyalty accounting actually works (that's
 * day 9's module, which doesn't exist yet). Until then, NoopLoyaltyAwarder
 * (infrastructure) implements this as a no-op so the flow still completes.
 */
public interface LoyaltyAwarder {

    void award(UUID customerId, int points);
}
