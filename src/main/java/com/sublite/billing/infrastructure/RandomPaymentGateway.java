package com.sublite.billing.infrastructure;

import com.sublite.billing.domain.ChargeResult;
import com.sublite.billing.domain.PaymentGateway;
import com.sublite.shared.domain.Money;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Stands in for a real provider (there's no real one to call). The
 * ConcurrentHashMap is what makes it idempotent by key: two threads racing
 * with the same idempotencyKey both call charge(), but computeIfAbsent
 * guarantees the random outcome is only rolled once and the second caller
 * gets the same answer back - exactly how a real provider like Stripe
 * behaves with its own idempotency keys.
 */
@Component
public class RandomPaymentGateway implements PaymentGateway {

    private static final double DECLINE_RATE = 0.15;
    private static final double PROVIDER_ERROR_RATE = 0.05;

    private final Map<UUID, ChargeResult> resultsByIdempotencyKey = new ConcurrentHashMap<>();

    @Override
    public ChargeResult charge(UUID idempotencyKey, Money amount) {
        return resultsByIdempotencyKey.computeIfAbsent(idempotencyKey, key -> roll());
    }

    private ChargeResult roll() {
        double outcome = ThreadLocalRandom.current().nextDouble();
        if (outcome < DECLINE_RATE) {
            return new ChargeResult.Declined("INSUFFICIENT_FUNDS");
        }
        if (outcome < DECLINE_RATE + PROVIDER_ERROR_RATE) {
            return new ChargeResult.ProviderError("Payment provider timeout");
        }
        return new ChargeResult.Success(UUID.randomUUID().toString());
    }
}
