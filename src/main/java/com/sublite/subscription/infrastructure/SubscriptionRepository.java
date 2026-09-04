package com.sublite.subscription.infrastructure;

import com.sublite.subscription.domain.Subscription;
import com.sublite.subscription.domain.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    Optional<Subscription> findByCustomerIdAndStatusIn(UUID customerId, Collection<SubscriptionStatus> statuses);

    List<Subscription> findByStatusIn(Collection<SubscriptionStatus> statuses);

    /**
     * planPrice (and its own plan) are lazy - BillingOrchestrator reads
     * price/billingPeriod after this method's own short transaction has
     * already closed, and SubscriptionPurchaseService's purchase() response
     * needs the plan's name/code too, so both hops are fetched here rather
     * than plain findById() throwing LazyInitializationException on whichever
     * caller needed the one this query didn't cover.
     */
    @Query("SELECT s FROM Subscription s JOIN FETCH s.planPrice pp JOIN FETCH pp.plan WHERE s.id = :id")
    Optional<Subscription> findByIdWithPlanPrice(UUID id);

    /**
     * Same reasoning as findByIdWithPlanPrice(), plus one more hop: the
     * "my subscription" response also needs the plan's own name/code, not
     * just its price, so this fetches planPrice.plan too in the same query
     * rather than a second round trip.
     */
    @Query("""
            SELECT s FROM Subscription s
            JOIN FETCH s.planPrice pp
            JOIN FETCH pp.plan
            WHERE s.customerId = :customerId AND s.status IN :statuses
            """)
    Optional<Subscription> findByCustomerIdAndStatusInWithPlan(
            @Param("customerId") UUID customerId, @Param("statuses") Collection<SubscriptionStatus> statuses);
}
