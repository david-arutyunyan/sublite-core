package com.sublite.retention.api.dto;

import com.sublite.retention.application.RetentionFlowConfig;
import com.sublite.retention.domain.CancellationAttempt;
import com.sublite.retention.domain.CancellationAttemptStatus;
import com.sublite.retention.domain.RetentionOfferType;
import com.sublite.retention.domain.RetentionStepType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * currentStep is null once the attempt isn't IN_PROGRESS anymore
 * (RETAINED/CANCELLED) - see the controller, which only looks the step up
 * for attempts still in progress. Nothing meaningful to render as "next
 * step" for a flow that's already finished, and the active flow's steps
 * could have changed since (an admin edit) in a way that no longer lines
 * up with a completed attempt's stored step order anyway.
 */
public record CancellationAttemptResponse(
        UUID id,
        UUID subscriptionId,
        CancellationAttemptStatus status,
        String reason,
        UUID acceptedOfferId,
        Instant startedAt,
        Instant completedAt,
        CurrentStepResponse currentStep
) {
    public record CurrentStepResponse(
            RetentionStepType type,
            UUID offerId,
            String offerCode,
            RetentionOfferType offerType,
            Map<String, Object> offerParameters
    ) {
        static CurrentStepResponse from(RetentionFlowConfig.StepView step) {
            RetentionFlowConfig.OfferView offer = step.offer();
            return new CurrentStepResponse(
                    step.type(),
                    offer == null ? null : offer.offerId(),
                    offer == null ? null : offer.code(),
                    offer == null ? null : offer.type(),
                    offer == null ? null : offer.parameters()
            );
        }
    }

    public static CancellationAttemptResponse from(CancellationAttempt attempt, RetentionFlowConfig.StepView currentStep) {
        return new CancellationAttemptResponse(
                attempt.getId(),
                attempt.getSubscriptionId(),
                attempt.getStatus(),
                attempt.getReason(),
                // id-only access on a lazy proxy is safe outside the
                // transaction (the FK is already known); any other
                // accepted-offer field would need it fetched instead.
                attempt.getAcceptedOffer() == null ? null : attempt.getAcceptedOffer().getId(),
                attempt.getStartedAt(),
                attempt.getCompletedAt(),
                currentStep == null ? null : CurrentStepResponse.from(currentStep)
        );
    }
}
