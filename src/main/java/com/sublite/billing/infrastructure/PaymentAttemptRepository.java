package com.sublite.billing.infrastructure;

import com.sublite.billing.domain.PaymentAttempt;
import com.sublite.billing.domain.PaymentAttemptStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, UUID> {

    Optional<PaymentAttempt> findByIdempotencyKey(UUID idempotencyKey);

    Optional<PaymentAttempt> findByInvoiceIdAndStatus(UUID invoiceId, PaymentAttemptStatus status);
}
