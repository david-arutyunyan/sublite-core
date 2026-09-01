package com.sublite.subscription.infrastructure;

import com.sublite.subscription.domain.Subscription;
import com.sublite.subscription.domain.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    Optional<Subscription> findByCustomerIdAndStatusIn(UUID customerId, Collection<SubscriptionStatus> statuses);

    List<Subscription> findByStatusIn(Collection<SubscriptionStatus> statuses);
}
