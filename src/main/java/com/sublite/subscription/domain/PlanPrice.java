package com.sublite.subscription.domain;

import com.sublite.shared.domain.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
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
 * The "valid_period" column (a Postgres tstzrange) is intentionally not
 * mapped here yet — plain JPA has no built-in range type, and nothing reads
 * or writes it from Java before we implement plan creation / price lookup
 * (later days). It's still enforced by the EXCLUDE constraint in the DB.
 */
@Entity
@Table(name = "plan_prices", schema = "subscription")
public class PlanPrice {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_period", nullable = false, length = 10)
    private BillingPeriod billingPeriod;

    @Embedded
    private Money price;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PlanPrice() {
        // JPA
    }

    public PlanPrice(UUID id, Plan plan, BillingPeriod billingPeriod, Money price, Instant createdAt) {
        this.id = id;
        this.plan = plan;
        this.billingPeriod = billingPeriod;
        this.price = price;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public Plan getPlan() {
        return plan;
    }

    public BillingPeriod getBillingPeriod() {
        return billingPeriod;
    }

    public Money getPrice() {
        return price;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
