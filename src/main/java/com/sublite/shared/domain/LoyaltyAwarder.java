package com.sublite.shared.domain;

import java.util.UUID;

/**
 * A port in "shared", not in whichever module happens to use it: it
 * started in retention.domain (day 7-8, the only consumer at the time),
 * and moved here once billing (day 9) also needed it - a second consumer
 * is usually the signal that a port belongs one level up, not in whichever
 * module reached for it first.
 */
public interface LoyaltyAwarder {

    void award(UUID customerId, int points, String reason);
}
