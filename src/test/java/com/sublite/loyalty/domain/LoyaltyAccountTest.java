package com.sublite.loyalty.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoyaltyAccountTest {

    private final Instant now = Instant.parse("2026-09-01T00:00:00Z");

    @Test
    void newAccountStartsAtZero() {
        LoyaltyAccount account = new LoyaltyAccount(UUID.randomUUID(), UUID.randomUUID(), now);

        assertThat(account.getBalance()).isZero();
    }

    @Test
    void creditIncreasesBalance() {
        LoyaltyAccount account = new LoyaltyAccount(UUID.randomUUID(), UUID.randomUUID(), now);

        account.credit(100, now);

        assertThat(account.getBalance()).isEqualTo(100);
    }

    @Test
    void debitDecreasesBalanceWhenSufficient() {
        LoyaltyAccount account = new LoyaltyAccount(UUID.randomUUID(), UUID.randomUUID(), now);
        account.credit(100, now);

        account.debit(40, now);

        assertThat(account.getBalance()).isEqualTo(60);
    }

    @Test
    void debitBeyondBalanceThrows() {
        LoyaltyAccount account = new LoyaltyAccount(UUID.randomUUID(), UUID.randomUUID(), now);
        account.credit(50, now);

        assertThatThrownBy(() -> account.debit(51, now))
                .isInstanceOf(InsufficientLoyaltyPointsException.class);

        assertThat(account.getBalance()).isEqualTo(50);
    }

    @Test
    void creditingZeroOrNegativeIsRejected() {
        LoyaltyAccount account = new LoyaltyAccount(UUID.randomUUID(), UUID.randomUUID(), now);

        assertThatThrownBy(() -> account.credit(0, now)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> account.credit(-5, now)).isInstanceOf(IllegalArgumentException.class);
    }
}
