package com.sublite.subscription.infrastructure;

import com.sublite.subscription.domain.SubscriptionStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SubscriptionStatusHistoryRepository extends JpaRepository<SubscriptionStatusHistory, UUID> {
}
