package com.sublite.billing.application;

import com.sublite.billing.domain.PaymentAttempt;
import com.sublite.billing.infrastructure.PaymentAttemptRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * A separate bean, not a private method on BillingService: REQUIRES_NEW
 * only takes effect through Spring's proxy, which only intercepts calls
 * arriving from OUTSIDE the class. Its job is narrow on purpose - Postgres
 * marks the whole transaction unusable after any statement fails, so the
 * risky insert needs its own transaction/connection. If it fails, that
 * failure rolls back only this transaction, leaving the caller's original
 * transaction (and its later fallback read) unaffected.
 */
@Component
class PaymentAttemptWriter {

    private final PaymentAttemptRepository paymentAttempts;

    PaymentAttemptWriter(PaymentAttemptRepository paymentAttempts) {
        this.paymentAttempts = paymentAttempts;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentAttempt insert(PaymentAttempt attempt) {
        return paymentAttempts.saveAndFlush(attempt);
    }
}
