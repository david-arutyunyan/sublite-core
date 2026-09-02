package com.sublite.subscription.infrastructure;

import com.sublite.subscription.domain.Subscription;
import com.sublite.subscription.domain.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    Optional<Subscription> findByCustomerIdAndStatusIn(UUID customerId, Collection<SubscriptionStatus> statuses);

    List<Subscription> findByStatusIn(Collection<SubscriptionStatus> statuses);

    /**
     * planPrice is lazy - BillingOrchestrator reads it (price, billing
     * period) after this method's own short transaction has already
     * closed, so a plain findById() would throw LazyInitializationException.
     * JOIN FETCH loads it eagerly in this one query instead.
     */
    @Query("SELECT s FROM Subscription s JOIN FETCH s.planPrice WHERE s.id = :id")
    Optional<Subscription> findByIdWithPlanPrice(UUID id);
}
