package com.sublite.loyalty.application;

import com.sublite.loyalty.domain.LoyaltyEventType;
import com.sublite.loyalty.domain.LoyaltyRule;
import com.sublite.loyalty.infrastructure.LoyaltyRuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
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

    private static final Logger log = LoggerFactory.getLogger(LoyaltyRuleAdminService.class);
    private static final int MAX_ATTEMPTS = 5;

    private final LoyaltyRuleRepository rules;
    private final LoyaltyRuleWriter writer;
    private final Clock clock;

    public LoyaltyRuleAdminService(LoyaltyRuleRepository rules, LoyaltyRuleWriter writer, Clock clock) {
        this.rules = rules;
        this.writer = writer;
        this.clock = clock;
    }

    /**
     * "Changing" a rule's points is really deactivate-then-create, same as
     * plan price versioning: loyalty_rules only allows one active row per
     * event type (V21), so whatever was active for this event type stops
     * governing new awards the moment this commits, and the old row stays
     * around as history rather than being overwritten.
     *
     * Two concurrent calls for the same event type (two admins, or a
     * double-submitted form) can both pass deactivateActive() before either
     * commits its new row, leaving both inserts racing for the same unique
     * slot - the loser's DataIntegrityViolationException has to be caught
     * OUTSIDE writer.setRuleOnce()'s own REQUIRES_NEW transaction (see its
     * javadoc), which is why this method itself isn't @Transactional and
     * just retries against the writer bean instead.
     */
    public LoyaltyRule setRule(LoyaltyEventType eventType, int points) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                LoyaltyRule rule = writer.setRuleOnce(eventType, points, Instant.now(clock));
                log.info("Loyalty rule set: eventType={}, points={}", eventType, points);
                return rule;
            } catch (DataIntegrityViolationException lostRace) {
                if (attempt == MAX_ATTEMPTS) {
                    throw lostRace;
                }
            }
        }
        throw new IllegalStateException("unreachable");
    }

    @Transactional(readOnly = true)
    public List<LoyaltyRule> listRules() {
        return rules.findAll();
    }
}
