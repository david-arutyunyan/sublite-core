package com.sublite.billing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * The idempotencyKey column has a DB unique constraint (V12) - that
 * constraint, not any in-memory check, is what actually prevents two
 * concurrent callers from recording the same charge twice. See
 * BillingService.chargeInvoice().
 */
@Entity
@Table(name = "payment_attempts", schema = "billing")
public class PaymentAttempt {

    @Id
    private UUID id;

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    @Column(name = "idempotency_key", nullable = false)
    private UUID idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentAttemptStatus status;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "attempted_at", nullable = false)
    private Instant attemptedAt;

    protected PaymentAttempt() {
        // JPA
    }

    public PaymentAttempt(
            UUID id,
            UUID invoiceId,
            UUID idempotencyKey,
            PaymentAttemptStatus status,
            String failureReason,
            Instant attemptedAt
    ) {
        this.id = id;
        this.invoiceId = invoiceId;
        this.idempotencyKey = idempotencyKey;
        this.status = status;
        this.failureReason = failureReason;
        this.attemptedAt = attemptedAt;
    }

    public boolean succeeded() {
        return status == PaymentAttemptStatus.SUCCEEDED;
    }

    public UUID getId() {
        return id;
    }

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public UUID getIdempotencyKey() {
        return idempotencyKey;
    }

    public PaymentAttemptStatus getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getAttemptedAt() {
        return attemptedAt;
    }
}
