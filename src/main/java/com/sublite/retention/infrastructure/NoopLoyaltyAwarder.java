package com.sublite.retention.infrastructure;

import com.sublite.retention.domain.LoyaltyAwarder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Stand-in until day 9 adds a real loyalty module and its own
 * LoyaltyAwarder bean (Spring would then need @Primary or this class
 * removed - a real second implementation isn't wired up yet).
 */
@Component
public class NoopLoyaltyAwarder implements LoyaltyAwarder {

    private static final Logger log = LoggerFactory.getLogger(NoopLoyaltyAwarder.class);

    @Override
    public void award(UUID customerId, int points) {
        log.warn("Loyalty module not implemented yet - would award {} points to customer {}", points, customerId);
    }
}
