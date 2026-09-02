package com.sublite.retention.application;

import com.sublite.retention.domain.RetentionOfferType;
import com.sublite.retention.domain.RetentionStepType;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A flattened read-model of the active flow, separate from the JPA
 * entities on purpose - what goes into Redis needs to serialize cleanly
 * to JSON and back, and JPA entities (lazy proxies, bidirectional
 * references) are a poor fit for that even when they'd technically work.
 */
public record RetentionFlowConfig(List<StepView> steps) {

    public record StepView(UUID stepId, int stepOrder, RetentionStepType type, OfferView offer) {
    }

    public record OfferView(UUID offerId, String code, RetentionOfferType type, Map<String, Object> parameters) {
    }
}
