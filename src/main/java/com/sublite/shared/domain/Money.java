package com.sublite.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Embeddable as a record: Hibernate 6+ can use a record directly as a JPA
 * embeddable, so this stays an immutable value object instead of needing
 * a mutable Lombok-style class just to satisfy JPA.
 */
@Embeddable
public record Money(
        @Column(name = "amount", precision = 10, scale = 2, nullable = false)
        BigDecimal amount,

        @Column(name = "currency", length = 3, nullable = false)
        String currency
) {
    public Money {
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("amount must not be negative: " + amount);
        }
        if (currency.length() != 3) {
            throw new IllegalArgumentException("currency must be a 3-letter ISO 4217 code: " + currency);
        }
    }
}
