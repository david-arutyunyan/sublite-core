package com.sublite.billing.application;

import com.sublite.billing.domain.ChargeResult;
import com.sublite.billing.domain.Invoice;
import com.sublite.billing.domain.PaymentAttempt;
import com.sublite.billing.domain.PaymentGateway;
import com.sublite.billing.infrastructure.InvoiceRepository;
import com.sublite.billing.infrastructure.PaymentAttemptRepository;
import com.sublite.shared.domain.Money;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A separate test class (not BillingServiceIdempotencyTest) specifically so
 * the gateway can be mocked to a deterministic Success - RandomPaymentGateway
 * would make "was it already PAID" flaky to set up. Covers the double-charge
 * gap: chargeInvoice() used to never check invoice.getStatus(), so calling
 * it a second time for an already-PAID invoice (a scheduler misfire, a
 * future manual retry endpoint, anything) would charge the customer again.
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "sublite.billing.scheduler.enabled=false")
class BillingServicePaidInvoiceGuardTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Autowired
    private BillingService billingService;
    @Autowired
    private InvoiceRepository invoices;
    @Autowired
    private PaymentAttemptRepository paymentAttempts;

    @MockitoBean
    private PaymentGateway paymentGateway;

    @Test
    void chargingAnAlreadyPaidInvoiceAgainDoesNotCallTheGatewayOrCreateASecondAttempt() {
        when(paymentGateway.charge(any(), any())).thenReturn(new ChargeResult.Success("ref-1"));
        Invoice invoice = invoices.save(new Invoice(
                UUID.randomUUID(), UUID.randomUUID(), Instant.now(), Instant.now().plus(Duration.ofDays(30)),
                new Money(BigDecimal.valueOf(9.99), "USD"), Instant.now()
        ));

        PaymentAttempt firstAttempt = billingService.chargeInvoice(invoice.getId(), UUID.randomUUID());
        assertThat(firstAttempt.succeeded()).isTrue();

        // Simulates processOne() being invoked a second time for the same
        // invoice (e.g. a scheduler race) - a fresh idempotency key, same
        // as a real second call would use, so only the invoice-status
        // guard (not the idempotency-key check) can catch this.
        PaymentAttempt secondAttempt = billingService.chargeInvoice(invoice.getId(), UUID.randomUUID());

        assertThat(secondAttempt.getId()).isEqualTo(firstAttempt.getId());
        verify(paymentGateway, times(1)).charge(any(), any());
        assertThat(attemptsFor(invoice)).hasSize(1);
    }

    @Test
    void chargingAStillPendingInvoiceAgainIsAllowed() {
        when(paymentGateway.charge(any(), any())).thenReturn(new ChargeResult.Declined("INSUFFICIENT_FUNDS"));
        Invoice invoice = invoices.save(new Invoice(
                UUID.randomUUID(), UUID.randomUUID(), Instant.now(), Instant.now().plus(Duration.ofDays(30)),
                new Money(BigDecimal.valueOf(9.99), "USD"), Instant.now()
        ));

        PaymentAttempt firstAttempt = billingService.chargeInvoice(invoice.getId(), UUID.randomUUID());
        assertThat(firstAttempt.succeeded()).isFalse();

        // A retry after a decline must still actually call the gateway
        // again - the PAID guard must not accidentally block legitimate
        // scheduler retries of a still-PENDING invoice.
        PaymentAttempt secondAttempt = billingService.chargeInvoice(invoice.getId(), UUID.randomUUID());

        assertThat(secondAttempt.getId()).isNotEqualTo(firstAttempt.getId());
        verify(paymentGateway, times(2)).charge(any(), any());
        assertThat(attemptsFor(invoice)).hasSize(2);
    }

    private List<PaymentAttempt> attemptsFor(Invoice invoice) {
        return paymentAttempts.findAll().stream()
                .filter(attempt -> attempt.getInvoiceId().equals(invoice.getId()))
                .toList();
    }
}
