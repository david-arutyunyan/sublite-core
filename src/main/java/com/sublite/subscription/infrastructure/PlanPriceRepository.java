package com.sublite.subscription.infrastructure;

import com.sublite.subscription.domain.PlanPrice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PlanPriceRepository extends JpaRepository<PlanPrice, UUID> {
}
