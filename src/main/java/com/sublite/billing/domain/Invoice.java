package com.sublite.billing.domain;

import com.sublite.shared.domain.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * subscriptionId is a plain UUID, not a JPA relation - see the comment on
 * V11's migration for why there isn't even a DB-level FK here.
 */
@Entity
@Table(name = "invoices", schema = "billing")
public class Invoice {

    @Id
    private UUID id;

    @Column(name = "subscription_id", nullable = false)
    private UUID subscriptionId;

    @Column(name = "period_start", nullable = false)
    private Instant periodStart;

    @Column(name = "period_end", nullable = false)
    private Instant periodEnd;

    @Embedded
    private Money amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvoiceStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Invoice() {
        // JPA
    }

    public Invoice(UUID id, UUID subscriptionId, Instant periodStart, Instant periodEnd, Money amount, Instant createdAt) {
        this.id = id;
        this.subscriptionId = subscriptionId;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.amount = amount;
        this.status = InvoiceStatus.PENDING;
        this.createdAt = createdAt;
    }

    public void markPaid() {
        this.status = InvoiceStatus.PAID;
    }

    public void markFailed() {
        this.status = InvoiceStatus.FAILED;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSubscriptionId() {
        return subscriptionId;
    }

    public Instant getPeriodStart() {
        return periodStart;
    }

    public Instant getPeriodEnd() {
        return periodEnd;
    }

    public Money getAmount() {
        return amount;
    }

    public InvoiceStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
