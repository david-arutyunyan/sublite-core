package com.sublite.loyalty.infrastructure;

import com.sublite.loyalty.domain.LoyaltyTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LoyaltyTransactionRepository extends JpaRepository<LoyaltyTransaction, UUID> {

    List<LoyaltyTransaction> findByAccountIdOrderByOccurredAtDesc(UUID accountId);
}
