package com.sublite.subscription.application;

import com.sublite.subscription.domain.Subscription;
import com.sublite.subscription.domain.SubscriptionEvent;
import com.sublite.subscription.domain.SubscriptionState;
import com.sublite.subscription.domain.SubscriptionStatus;
import com.sublite.subscription.domain.SubscriptionStatusHistory;
import com.sublite.subscription.domain.SubscriptionTransitions;
import com.sublite.subscription.infrastructure.SubscriptionRepository;
import com.sublite.subscription.infrastructure.SubscriptionStatusHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * The use-case layer for lifecycle events: load, compute the next state via
 * SubscriptionTransitions, persist, and append one history row per
 * transition. Loading through SubscriptionRepository means the @Version
 * column on Subscription is what actually protects this method under
 * concurrent calls for the same id - see SubscriptionConcurrentUpdateTest.
 */
@Service
public class SubscriptionLifecycleService {

    private final SubscriptionRepository subscriptions;
    private final SubscriptionStatusHistoryRepository history;
    private final Clock clock;

    public SubscriptionLifecycleService(
            SubscriptionRepository subscriptions,
            SubscriptionStatusHistoryRepository history,
            Clock clock
    ) {
        this.subscriptions = subscriptions;
        this.history = history;
        this.clock = clock;
    }

    @Transactional
    public Subscription handle(UUID subscriptionId, SubscriptionEvent event) {
        Subscription subscription = subscriptions.findById(subscriptionId)
                .orElseThrow(() -> new NoSuchElementException("Subscription not found: " + subscriptionId));

        SubscriptionStatus fromStatus = subscription.getStatus();
        Instant now = clock.instant();

        SubscriptionState nextState = SubscriptionTransitions.apply(subscription.toState(), event, now);
        subscription.applyState(nextState, now);
        Subscription saved = subscriptions.save(subscription);

        history.save(new SubscriptionStatusHistory(
                UUID.randomUUID(),
                saved.getId(),
                fromStatus,
                saved.getStatus(),
                reasonOf(event),
                now
        ));

        return saved;
    }

    private static String reasonOf(SubscriptionEvent event) {
        return switch (event) {
            case SubscriptionEvent.CancelRequested cancelRequested -> cancelRequested.reason();
            case SubscriptionEvent.GracePeriodExpired ignored -> "PAYMENT_FAILED";
            case SubscriptionEvent.ChargeFailed ignored -> "CHARGE_FAILED";
            default -> null;
        };
    }
}
