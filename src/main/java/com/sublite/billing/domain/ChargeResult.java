package com.sublite.billing.domain;

/**
 * What a payment provider can tell us after a charge attempt. Declined and
 * ProviderError are deliberately separate: a decline is the customer's card
 * being refused (still counts as a failed attempt, feeds into the retry/
 * grace-period backoff), while a provider error means the request itself
 * didn't get a clear answer - in a real system that distinction would
 * usually also drive whether it's safe to retry immediately, but for this
 * project both are treated the same way by BillingOrchestrator.
 */
public sealed interface ChargeResult {

    record Success(String providerReference) implements ChargeResult {
    }

    record Declined(String reason) implements ChargeResult {
    }

    record ProviderError(String message) implements ChargeResult {
    }
}
