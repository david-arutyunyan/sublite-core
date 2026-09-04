package com.sublite.loyalty.api.dto;

import com.sublite.loyalty.domain.LoyaltyTransaction;
import com.sublite.loyalty.domain.LoyaltyTransactionType;

import java.time.Instant;
import java.util.UUID;

public record LoyaltyTransactionResponse(
        UUID id,
        LoyaltyTransactionType type,
        int points,
        String reason,
        Instant occurredAt
) {
    public static LoyaltyTransactionResponse from(LoyaltyTransaction transaction) {
        return new LoyaltyTransactionResponse(
                transaction.getId(),
                transaction.getType(),
                transaction.getPoints(),
                transaction.getReason(),
                transaction.getOccurredAt()
        );
    }
}
