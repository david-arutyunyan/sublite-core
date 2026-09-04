package com.sublite.loyalty.infrastructure;

import com.sublite.loyalty.domain.LoyaltyEventType;
import com.sublite.loyalty.domain.LoyaltyRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface LoyaltyRuleRepository extends JpaRepository<LoyaltyRule, UUID> {

    Optional<LoyaltyRule> findByEventTypeAndActiveTrue(LoyaltyEventType eventType);

    /**
     * The other half of "changing" a rule's points (see
     * LoyaltyRuleAdminService.setRule()): loyalty_rules only allows one
     * active row per event_type (partial unique index, V21) - same
     * versioning idea as plan prices, just without a validity range, since
     * nothing needs to know exactly when an old rule stopped applying.
     * clearAutomatically: the persistence context could otherwise still
     * think the row this just deactivated is active, for the rest of the
     * transaction.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE LoyaltyRule r SET r.active = false WHERE r.eventType = :eventType AND r.active = true")
    int deactivateActive(@Param("eventType") LoyaltyEventType eventType);
}
