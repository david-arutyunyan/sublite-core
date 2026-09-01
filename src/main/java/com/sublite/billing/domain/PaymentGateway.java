package com.sublite.billing.domain;

import com.sublite.shared.domain.Money;

import java.util.UUID;

/**
 * A port: billing depends on this interface, not on any concrete provider.
 * Real providers (Stripe and friends) are idempotent by key - calling
 * charge() twice with the same idempotencyKey should return the same
 * result rather than charging twice. Implementations are expected to honor
 * that contract; RandomPaymentGateway does.
 */
public interface PaymentGateway {

    ChargeResult charge(UUID idempotencyKey, Money amount);
}
