package com.sublite.loyalty.api.dto;

import com.sublite.loyalty.domain.LoyaltyEventType;
import com.sublite.loyalty.domain.LoyaltyRule;

import java.time.Instant;
import java.util.UUID;

public record LoyaltyRuleResponse(
        UUID id,
        LoyaltyEventType eventType,
        int points,
        boolean active,
        Instant createdAt
) {
    public static LoyaltyRuleResponse from(LoyaltyRule rule) {
        return new LoyaltyRuleResponse(
                rule.getId(),
                rule.getEventType(),
                rule.getPoints(),
                rule.isActive(),
                rule.getCreatedAt()
        );
    }
}
