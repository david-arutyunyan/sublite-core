package com.sublite.billing.application;

import com.sublite.billing.domain.ChargeResult;
import com.sublite.billing.domain.Invoice;
import com.sublite.billing.domain.PaymentAttempt;
import com.sublite.billing.domain.PaymentAttemptStatus;
import com.sublite.billing.domain.PaymentGateway;
import com.sublite.billing.infrastructure.InvoiceRepository;
import com.sublite.billing.infrastructure.PaymentAttemptRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * chargeInvoice() is idempotent in two layers:
 *  1. the up-front findByIdempotencyKey check is an optimization - skip
 *     calling the gateway at all if we already know the answer.
 *  2. PaymentAttemptWriter.insert() is what's actually correct under a
 *     race: if two callers both miss the check above, the DB's unique
 *     index on idempotency_key lets only one INSERT through. The loser
 *     catches DataIntegrityViolationException here and re-reads the
 *     winner's row - but that only works because the failed insert ran in
 *     its OWN transaction (see PaymentAttemptWriter): Postgres marks a
 *     whole transaction unusable after any statement in it fails, so
 *     without that separate transaction, the fallback read below would
 *     itself fail with "current transaction is aborted".
 * The gateway itself is also expected to be idempotent by key (see
 * RandomPaymentGateway), so even if both callers reach gateway.charge()
 * before either has inserted, they get the same ChargeResult back.
 */
@Service
public class BillingService {

    private final InvoiceRepository invoices;
    private final PaymentAttemptRepository paymentAttempts;
    private final PaymentAttemptWriter paymentAttemptWriter;
    private final PaymentGateway gateway;
    private final Clock clock;

    public BillingService(
            InvoiceRepository invoices,
            PaymentAttemptRepository paymentAttempts,
            PaymentAttemptWriter paymentAttemptWriter,
            PaymentGateway gateway,
            Clock clock
    ) {
        this.invoices = invoices;
        this.paymentAttempts = paymentAttempts;
        this.paymentAttemptWriter = paymentAttemptWriter;
        this.gateway = gateway;
        this.clock = clock;
    }

    @Transactional
    public PaymentAttempt chargeInvoice(UUID invoiceId, UUID idempotencyKey) {
        var existing = paymentAttempts.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return existing.get();
        }

        Invoice invoice = invoices.findById(invoiceId)
                .orElseThrow(() -> new NoSuchElementException("Invoice not found: " + invoiceId));

        ChargeResult result = gateway.charge(idempotencyKey, invoice.getAmount());

        PaymentAttempt attempt = new PaymentAttempt(
                UUID.randomUUID(),
                invoice.getId(),
                idempotencyKey,
                statusOf(result),
                reasonOf(result),
                clock.instant()
        );

        try {
            attempt = paymentAttemptWriter.insert(attempt);
        } catch (DataIntegrityViolationException raceLost) {
            return paymentAttempts.findByIdempotencyKey(idempotencyKey).orElseThrow(() -> raceLost);
        }

        if (result instanceof ChargeResult.Success) {
            invoice.markPaid();
        }

        return attempt;
    }

    private static PaymentAttemptStatus statusOf(ChargeResult result) {
        return switch (result) {
            case ChargeResult.Success ignored -> PaymentAttemptStatus.SUCCEEDED;
            case ChargeResult.Declined ignored -> PaymentAttemptStatus.DECLINED;
            case ChargeResult.ProviderError ignored -> PaymentAttemptStatus.PROVIDER_ERROR;
        };
    }

    private static String reasonOf(ChargeResult result) {
        return switch (result) {
            case ChargeResult.Success ignored -> null;
            case ChargeResult.Declined declined -> declined.reason();
            case ChargeResult.ProviderError providerError -> providerError.message();
        };
    }
}
