package com.sublite.loyalty.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * accountId IS a real @ManyToOne relation - LoyaltyAccount is the same
 * module, same as Plan/PlanPrice within subscription. Append-only: nothing
 * in this codebase ever updates or deletes a row here.
 */
@Entity
@Table(name = "loyalty_transactions", schema = "loyalty")
public class LoyaltyTransaction {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private LoyaltyAccount account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private LoyaltyTransactionType type;

    @Column(nullable = false)
    private int points;

    @Column(nullable = false, length = 100)
    private String reason;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected LoyaltyTransaction() {
        // JPA
    }

    public LoyaltyTransaction(UUID id, LoyaltyAccount account, LoyaltyTransactionType type, int points, String reason, Instant occurredAt) {
        this.id = id;
        this.account = account;
        this.type = type;
        this.points = points;
        this.reason = reason;
        this.occurredAt = occurredAt;
    }

    public UUID getId() {
        return id;
    }

    public LoyaltyAccount getAccount() {
        return account;
    }

    public LoyaltyTransactionType getType() {
        return type;
    }

    public int getPoints() {
        return points;
    }

    public String getReason() {
        return reason;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
