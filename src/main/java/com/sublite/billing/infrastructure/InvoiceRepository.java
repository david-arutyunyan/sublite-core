package com.sublite.billing.infrastructure;

import com.sublite.billing.domain.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    Optional<Invoice> findBySubscriptionIdAndPeriodStart(UUID subscriptionId, Instant periodStart);
}
