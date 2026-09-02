package com.sublite.retention.application;

import com.sublite.retention.domain.CancellationAttempt;
import com.sublite.retention.domain.InvalidCancellationStepException;
import com.sublite.retention.domain.LoyaltyAwarder;
import com.sublite.retention.domain.RetentionStepType;
import com.sublite.retention.infrastructure.CancellationAttemptRepository;
import com.sublite.retention.infrastructure.RetentionOfferRepository;
import com.sublite.subscription.application.SubscriptionLifecycleService;
import com.sublite.subscription.domain.SubscriptionEvent;
import com.sublite.subscription.infrastructure.SubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Comparator;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Reaches into the subscription module only through SubscriptionRepository
 * (read the customer id) and SubscriptionLifecycleService (drive the
 * actual pause/cancel), the same pattern BillingOrchestrator uses - never
 * through subscription's SubscriptionState/SubscriptionTransitions.
 *
 * A full sealed-interface state machine (like SubscriptionTransitions)
 * wasn't used here on purpose: this flow is a straight line with only
 * accept/decline branching at OFFER steps, not a graph with many possible
 * transitions per state - a switch over step type plus one validation
 * check covers it without the extra ceremony.
 */
@Service
public class RetentionFlowService {

    private final CancellationAttemptRepository attempts;
    private final RetentionOfferRepository offers;
    private final RetentionFlowConfigService flowConfig;
    private final SubscriptionRepository subscriptions;
    private final SubscriptionLifecycleService subscriptionLifecycle;
    private final LoyaltyAwarder loyaltyAwarder;
    private final Clock clock;

    public RetentionFlowService(
            CancellationAttemptRepository attempts,
            RetentionOfferRepository offers,
            RetentionFlowConfigService flowConfig,
            SubscriptionRepository subscriptions,
            SubscriptionLifecycleService subscriptionLifecycle,
            LoyaltyAwarder loyaltyAwarder,
            Clock clock
    ) {
        this.attempts = attempts;
        this.offers = offers;
        this.flowConfig = flowConfig;
        this.subscriptions = subscriptions;
        this.subscriptionLifecycle = subscriptionLifecycle;
        this.loyaltyAwarder = loyaltyAwarder;
        this.clock = clock;
    }

    @Transactional
    public CancellationAttempt start(UUID subscriptionId) {
        RetentionFlowConfig.StepView first = firstStep(flowConfig.getActiveFlow());
        CancellationAttempt attempt = new CancellationAttempt(UUID.randomUUID(), subscriptionId, first.stepOrder(), clock.instant());
        return attempts.save(attempt);
    }

    @Transactional
    public CancellationAttempt submitReason(UUID attemptId, String reason) {
        CancellationAttempt attempt = load(attemptId);
        RetentionFlowConfig.StepView current = requireStep(attempt, RetentionStepType.SURVEY);
        attempt.recordReason(reason, nextStep(current.stepOrder()).stepOrder());
        return attempt;
    }

    @Transactional
    public CancellationAttempt acceptCurrentOffer(UUID attemptId) {
        CancellationAttempt attempt = load(attemptId);
        RetentionFlowConfig.StepView current = requireStep(attempt, RetentionStepType.OFFER);
        RetentionFlowConfig.OfferView offer = current.offer();

        applyOfferEffect(attempt.getSubscriptionId(), offer);
        attempt.retain(offers.getReferenceById(offer.offerId()), clock.instant());
        return attempt;
    }

    @Transactional
    public CancellationAttempt declineCurrentOffer(UUID attemptId) {
        CancellationAttempt attempt = load(attemptId);
        RetentionFlowConfig.StepView current = requireStep(attempt, RetentionStepType.OFFER);
        attempt.advanceTo(nextStep(current.stepOrder()).stepOrder());
        return attempt;
    }

    @Transactional
    public CancellationAttempt confirmCancellation(UUID attemptId) {
        CancellationAttempt attempt = load(attemptId);
        requireStep(attempt, RetentionStepType.CONFIRMATION);

        subscriptionLifecycle.handle(attempt.getSubscriptionId(), new SubscriptionEvent.CancelRequested("USER_REQUESTED"));
        attempt.cancel(clock.instant());
        return attempt;
    }

    private void applyOfferEffect(UUID subscriptionId, RetentionFlowConfig.OfferView offer) {
        switch (offer.type()) {
            case PAUSE_SUBSCRIPTION ->
                    subscriptionLifecycle.handle(subscriptionId, new SubscriptionEvent.PauseRequested());
            case LOYALTY_POINTS -> {
                UUID customerId = subscriptions.findById(subscriptionId)
                        .orElseThrow(() -> new NoSuchElementException("Subscription not found: " + subscriptionId))
                        .getCustomerId();
                loyaltyAwarder.award(customerId, intParameter(offer, "points"));
            }
            case DISCOUNT_PERCENT -> {
                // Recorded via attempt.retain() below but not applied to
                // billing yet - that needs discount support on PlanPrice/
                // Invoice, which doesn't exist. A natural extension, not
                // built in this pass.
            }
        }
    }

    private static int intParameter(RetentionFlowConfig.OfferView offer, String key) {
        return offer.parameters().get(key) instanceof Number number ? number.intValue() : 0;
    }

    private RetentionFlowConfig.StepView requireStep(CancellationAttempt attempt, RetentionStepType expectedType) {
        RetentionFlowConfig.StepView step = stepAt(attempt.getCurrentStepOrder());
        if (step.type() != expectedType) {
            throw new InvalidCancellationStepException(
                    "Expected a %s step but attempt %s is at a %s step".formatted(expectedType, attempt.getId(), step.type())
            );
        }
        return step;
    }

    private RetentionFlowConfig.StepView stepAt(int stepOrder) {
        return flowConfig.getActiveFlow().steps().stream()
                .filter(step -> step.stepOrder() == stepOrder)
                .findFirst()
                .orElseThrow(() -> new InvalidCancellationStepException("No such step: " + stepOrder));
    }

    private RetentionFlowConfig.StepView nextStep(int currentOrder) {
        return flowConfig.getActiveFlow().steps().stream()
                .filter(step -> step.stepOrder() > currentOrder)
                .min(Comparator.comparingInt(RetentionFlowConfig.StepView::stepOrder))
                .orElseThrow(() -> new InvalidCancellationStepException("No step after order " + currentOrder));
    }

    private RetentionFlowConfig.StepView firstStep(RetentionFlowConfig flow) {
        return flow.steps().stream()
                .min(Comparator.comparingInt(RetentionFlowConfig.StepView::stepOrder))
                .orElseThrow(() -> new IllegalStateException("Retention flow has no active steps configured"));
    }

    private CancellationAttempt load(UUID attemptId) {
        return attempts.findById(attemptId)
                .orElseThrow(() -> new NoSuchElementException("Cancellation attempt not found: " + attemptId));
    }
}
