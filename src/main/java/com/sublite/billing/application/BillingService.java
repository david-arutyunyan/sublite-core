package com.sublite.billing.application;

import com.sublite.billing.domain.ChargeResult;
import com.sublite.billing.domain.Invoice;
import com.sublite.billing.domain.InvoiceStatus;
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
 * chargeInvoice() is idempotent in three layers:
 *  1. the invoice.getStatus() == PAID check below is the outermost guard:
 *     an invoice that's already been successfully charged must never be
 *     charged again, no matter why chargeInvoice() got called a second
 *     time for it (a scheduler misfire, a future manual "retry billing"
 *     admin action, anything). This is deliberately NOT "status must be
 *     PENDING" - a PENDING invoice legitimately gets charged again on
 *     every scheduler retry after a decline, and that's correct.
 *  2. the up-front findByIdempotencyKey check is an optimization within
 *     ONE logical charge attempt - skip calling the gateway at all if
 *     this exact call already ran. It deliberately does NOT protect
 *     across separate retries of the same invoice: idempotencyKey is a
 *     fresh UUID per call (see BillingOrchestrator.processOne()) exactly
 *     so a retry after a decline can actually attempt a new charge
 *     instead of replaying the old failure forever.
 *  3. PaymentAttemptWriter.insert() is what's actually correct under a
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

        if (invoice.getStatus() == InvoiceStatus.PAID) {
            return paymentAttempts.findByInvoiceIdAndStatus(invoiceId, PaymentAttemptStatus.SUCCEEDED)
                    .orElseThrow(() -> new IllegalStateException("Invoice " + invoiceId + " is PAID but has no SUCCEEDED payment attempt"));
        }

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
