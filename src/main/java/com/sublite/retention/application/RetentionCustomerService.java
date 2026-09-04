package com.sublite.retention.application;

import com.sublite.retention.domain.CancellationAttempt;
import com.sublite.retention.domain.CancellationAttemptNotFoundException;
import com.sublite.retention.infrastructure.CancellationAttemptRepository;
import com.sublite.subscription.domain.Subscription;
import com.sublite.subscription.domain.SubscriptionNotFoundException;
import com.sublite.subscription.infrastructure.SubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * RetentionFlowService itself has no concept of "whose" subscription or
 * attempt it's operating on - start(subscriptionId) and every other
 * method just trust the id they're given. That's fine for
 * RetentionFlowService's own tests (they call it directly), but wrong for
 * a REST API: without an ownership check here, one customer could drive
 * another customer's cancellation flow by guessing/enumerating a UUID.
 * This service is that check, kept separate from RetentionFlowService
 * itself rather than threading a customerId parameter through every one
 * of its methods (which has nothing to do with the flow's own state
 * machine, only with who's allowed to poke it from the outside).
 */
@Service
public class RetentionCustomerService {

    private final RetentionFlowService flowService;
    private final CancellationAttemptRepository attempts;
    private final SubscriptionRepository subscriptions;

    public RetentionCustomerService(
            RetentionFlowService flowService,
            CancellationAttemptRepository attempts,
            SubscriptionRepository subscriptions
    ) {
        this.flowService = flowService;
        this.attempts = attempts;
        this.subscriptions = subscriptions;
    }

    @Transactional
    public CancellationAttempt start(UUID customerId, UUID subscriptionId) {
        requireOwnSubscription(customerId, subscriptionId);
        return flowService.start(subscriptionId);
    }

    @Transactional(readOnly = true)
    public CancellationAttempt get(UUID customerId, UUID attemptId) {
        return requireOwnAttempt(customerId, attemptId);
    }

    @Transactional
    public CancellationAttempt submitReason(UUID customerId, UUID attemptId, String reason) {
        requireOwnAttempt(customerId, attemptId);
        return flowService.submitReason(attemptId, reason);
    }

    @Transactional
    public CancellationAttempt acceptCurrentOffer(UUID customerId, UUID attemptId) {
        requireOwnAttempt(customerId, attemptId);
        return flowService.acceptCurrentOffer(attemptId);
    }

    @Transactional
    public CancellationAttempt declineCurrentOffer(UUID customerId, UUID attemptId) {
        requireOwnAttempt(customerId, attemptId);
        return flowService.declineCurrentOffer(attemptId);
    }

    @Transactional
    public CancellationAttempt confirmCancellation(UUID customerId, UUID attemptId) {
        requireOwnAttempt(customerId, attemptId);
        return flowService.confirmCancellation(attemptId);
    }

    /**
     * Loads the attempt twice on every call after this one - once here for
     * the ownership check, once more inside whichever RetentionFlowService
     * method actually runs. Simple and correct beats threading an
     * already-loaded entity through a service that was never designed to
     * take one; two cheap primary-key reads aren't worth avoiding at this
     * scale.
     */
    private CancellationAttempt requireOwnAttempt(UUID customerId, UUID attemptId) {
        CancellationAttempt attempt = attempts.findById(attemptId)
                .orElseThrow(() -> new CancellationAttemptNotFoundException(attemptId));
        requireOwnSubscription(customerId, attempt.getSubscriptionId());
        return attempt;
    }

    private void requireOwnSubscription(UUID customerId, UUID subscriptionId) {
        Subscription subscription = subscriptions.findById(subscriptionId)
                .orElseThrow(() -> new SubscriptionNotFoundException(subscriptionId));
        if (!subscription.getCustomerId().equals(customerId)) {
            throw new SubscriptionNotFoundException(subscriptionId);
        }
    }
}
