package com.sublite.loyalty.application;

import com.sublite.loyalty.domain.LoyaltyEventType;
import com.sublite.loyalty.domain.LoyaltyRule;
import com.sublite.loyalty.infrastructure.LoyaltyRuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * No cache to evict here, unlike RetentionAdminService - LoyaltyService
 * reads the active rule straight from Postgres on every award, there's no
 * Redis layer in front of it (only the retention flow config is cached,
 * per the spec).
 */
@Service
public class LoyaltyRuleAdminService {

    private final LoyaltyRuleRepository rules;
    private final Clock clock;

    public LoyaltyRuleAdminService(LoyaltyRuleRepository rules, Clock clock) {
        this.rules = rules;
        this.clock = clock;
    }

    /**
     * "Changing" a rule's points is really deactivate-then-create, same as
     * plan price versioning: loyalty_rules only allows one active row per
     * event type (V21), so whatever was active for this event type stops
     * governing new awards the moment this commits, and the old row stays
     * around as history rather than being overwritten.
     */
    @Transactional
    public LoyaltyRule setRule(LoyaltyEventType eventType, int points) {
        rules.deactivateActive(eventType);
        return rules.save(new LoyaltyRule(UUID.randomUUID(), eventType, points, Instant.now(clock)));
    }

    @Transactional(readOnly = true)
    public List<LoyaltyRule> listRules() {
        return rules.findAll();
    }
}
