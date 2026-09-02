package com.sublite.loyalty.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * balance is a denormalized running total, kept in sync with
 * LoyaltyTransaction in the same transaction as every credit/debit - see
 * LoyaltyService. It exists purely so reading a balance doesn't mean
 * summing the whole ledger every time.
 */
@Entity
@Table(name = "loyalty_accounts", schema = "loyalty")
public class LoyaltyAccount {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(nullable = false)
    private int balance;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LoyaltyAccount() {
        // JPA
    }

    public LoyaltyAccount(UUID id, UUID customerId, Instant createdAt) {
        this.id = id;
        this.customerId = customerId;
        this.balance = 0;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public void credit(int points, Instant now) {
        if (points <= 0) {
            throw new IllegalArgumentException("points must be positive: " + points);
        }
        this.balance += points;
        this.updatedAt = now;
    }

    public void debit(int points, Instant now) {
        if (points <= 0) {
            throw new IllegalArgumentException("points must be positive: " + points);
        }
        if (this.balance < points) {
            throw new InsufficientLoyaltyPointsException(customerId, points, this.balance);
        }
        this.balance -= points;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public int getBalance() {
        return balance;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
