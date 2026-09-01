package com.sublite.billing.application;

import com.sublite.billing.domain.Invoice;
import com.sublite.billing.domain.PaymentAttempt;
import com.sublite.billing.infrastructure.InvoiceRepository;
import com.sublite.billing.infrastructure.PaymentAttemptRepository;
import com.sublite.shared.domain.Money;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The concurrent-charge test the project explicitly calls for: two threads
 * call chargeInvoice() with the SAME idempotency key at (as close to) the
 * same instant, simulating e.g. a network retry or two scheduler instances
 * racing before ShedLock has kicked in. Exactly one payment_attempts row
 * must exist for that key afterwards, and both callers must see it.
 *
 * The scheduler is disabled here: @SpringBootTest boots the real
 * BillingScheduler bean too, and this test's invoice could otherwise get
 * raced by the actual background job.
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "sublite.billing.scheduler.enabled=false")
class BillingServiceIdempotencyTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Autowired
    private BillingService billingService;
    @Autowired
    private InvoiceRepository invoices;
    @Autowired
    private PaymentAttemptRepository paymentAttempts;

    @Test
    void concurrentChargesWithTheSameIdempotencyKeyProduceExactlyOneAttempt() throws Exception {
        Instant now = Instant.now();
        Invoice invoice = invoices.save(new Invoice(
                UUID.randomUUID(),
                UUID.randomUUID(),
                now,
                now.plus(Duration.ofDays(30)),
                new Money(BigDecimal.valueOf(9.99), "USD"),
                now
        ));
        UUID idempotencyKey = UUID.randomUUID();
        CyclicBarrier bothReady = new CyclicBarrier(2);

        Callable<PaymentAttempt> chargeTask = () -> {
            bothReady.await();
            return billingService.chargeInvoice(invoice.getId(), idempotencyKey);
        };

        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<PaymentAttempt>> results = pool.invokeAll(List.of(chargeTask, chargeTask));
        pool.shutdown();

        PaymentAttempt first = results.get(0).get();
        PaymentAttempt second = results.get(1).get();

        assertThat(first.getId()).isEqualTo(second.getId());
        assertThat(first.getStatus()).isEqualTo(second.getStatus());

        long attemptsWithThisKey = paymentAttempts.findAll().stream()
                .filter(a -> a.getIdempotencyKey().equals(idempotencyKey))
                .count();
        assertThat(attemptsWithThisKey).isEqualTo(1);
    }

    @Test
    void repeatedCallWithTheSameKeyAfterTheFactReturnsTheStoredAttempt() {
        Instant now = Instant.now();
        Invoice invoice = invoices.save(new Invoice(
                UUID.randomUUID(),
                UUID.randomUUID(),
                now,
                now.plus(Duration.ofDays(30)),
                new Money(BigDecimal.valueOf(9.99), "USD"),
                now
        ));
        UUID idempotencyKey = UUID.randomUUID();

        PaymentAttempt firstCall = billingService.chargeInvoice(invoice.getId(), idempotencyKey);
        PaymentAttempt secondCall = billingService.chargeInvoice(invoice.getId(), idempotencyKey);

        assertThat(secondCall.getId()).isEqualTo(firstCall.getId());
        assertThat(paymentAttempts.findAll()).hasSize(1);
    }
}
