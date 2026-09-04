package com.sublite.retention.api.dto;

import com.sublite.retention.domain.RetentionOffer;
import com.sublite.retention.domain.RetentionOfferType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record RetentionOfferResponse(
        UUID id,
        String code,
        RetentionOfferType type,
        Map<String, Object> parameters,
        boolean active,
        Instant createdAt
) {
    public static RetentionOfferResponse from(RetentionOffer offer) {
        return new RetentionOfferResponse(
                offer.getId(),
                offer.getCode(),
                offer.getType(),
                offer.getParameters(),
                offer.isActive(),
                offer.getCreatedAt()
        );
    }
}
