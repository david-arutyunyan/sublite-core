package com.sublite.loyalty.application;

import com.sublite.loyalty.domain.LoyaltyEventType;
import com.sublite.loyalty.domain.LoyaltyRule;
import com.sublite.loyalty.infrastructure.LoyaltyRuleRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Same pattern as LoyaltyAccountWriter, for the same reason: two concurrent
 * "set the active rule for this event type" calls both deactivate whatever
 * was active, then both try to insert a new active row - the partial
 * unique index (uq_loyalty_rules_active_event_type, V21) lets exactly one
 * of those inserts through. Catching that constraint violation only works
 * cleanly if the failed insert happened in its own transaction (a failed
 * statement poisons whatever transaction it ran in), so this has to be a
 * REQUIRES_NEW method the caller retries from outside its boundary - see
 * LoyaltyRuleAdminService.setRule() and LoyaltyAccountWriter.creditOnce()'s
 * own javadoc for why catching inside would still fail with
 * UnexpectedRollbackException.
 */
@Component
class LoyaltyRuleWriter {

    private final LoyaltyRuleRepository rules;

    LoyaltyRuleWriter(LoyaltyRuleRepository rules) {
        this.rules = rules;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LoyaltyRule setRuleOnce(LoyaltyEventType eventType, int points, Instant now) {
        rules.deactivateActive(eventType);
        return rules.saveAndFlush(new LoyaltyRule(UUID.randomUUID(), eventType, points, now));
    }
}
