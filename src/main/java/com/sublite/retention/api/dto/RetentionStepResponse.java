package com.sublite.retention.api.dto;

import com.sublite.retention.domain.RetentionStep;
import com.sublite.retention.domain.RetentionStepType;

import java.time.Instant;
import java.util.UUID;

public record RetentionStepResponse(
        UUID id,
        int stepOrder,
        RetentionStepType type,
        UUID offerId,
        boolean active,
        Instant createdAt
) {
    /**
     * getOffer().getId() here is safe even though `offer` is
     * fetch = LAZY and open-in-view is off (application.yml) - a
     * Hibernate proxy's id is populated from the foreign key at creation,
     * before the transaction that loaded the owning RetentionStep closes,
     * so reading just the id never needs to re-hit the database. Calling
     * any other offer getter here (code, type, parameters) would throw
     * LazyInitializationException once this runs outside that transaction.
     */
    public static RetentionStepResponse from(RetentionStep step) {
        return new RetentionStepResponse(
                step.getId(),
                step.getStepOrder(),
                step.getType(),
                step.getOffer() == null ? null : step.getOffer().getId(),
                step.isActive(),
                step.getCreatedAt()
        );
    }
}
