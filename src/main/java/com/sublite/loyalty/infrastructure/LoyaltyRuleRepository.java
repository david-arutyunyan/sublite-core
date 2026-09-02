package com.sublite.loyalty.infrastructure;

import com.sublite.loyalty.domain.LoyaltyEventType;
import com.sublite.loyalty.domain.LoyaltyRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LoyaltyRuleRepository extends JpaRepository<LoyaltyRule, UUID> {

    Optional<LoyaltyRule> findByEventTypeAndActiveTrue(LoyaltyEventType eventType);
}
