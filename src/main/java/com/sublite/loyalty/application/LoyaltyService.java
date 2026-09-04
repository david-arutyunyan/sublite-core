package com.sublite.loyalty.application;

import com.sublite.loyalty.domain.InsufficientLoyaltyPointsException;
import com.sublite.loyalty.domain.LoyaltyAccount;
import com.sublite.loyalty.domain.LoyaltyEventType;
import com.sublite.loyalty.domain.LoyaltyTransaction;
import com.sublite.loyalty.domain.LoyaltyTransactionType;
import com.sublite.loyalty.infrastructure.LoyaltyAccountRepository;
import com.sublite.loyalty.infrastructure.LoyaltyRuleRepository;
import com.sublite.loyalty.infrastructure.LoyaltyTransactionRepository;
import com.sublite.shared.domain.LoyaltyAwarder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Implements the LoyaltyAwarder port (award: caller already knows how many
 * points, e.g. retention's offer config) and also exposes its own richer
 * API for callers within this codebase that don't need to be decoupled
 * through a port - awardForEvent() is what billing calls, letting THIS
 * module decide the amount via its own configured LoyaltyRule instead of
 * the caller hardcoding it.
 */
@Service
public class LoyaltyService implements LoyaltyAwarder {

    private static final Logger log = LoggerFactory.getLogger(LoyaltyService.class);

    private static final int MAX_CREDIT_ATTEMPTS = 5;

    private final LoyaltyAccountRepository accounts;
    private final LoyaltyAccountWriter accountWriter;
    private final LoyaltyTransactionRepository transactions;
    private final LoyaltyRuleRepository rules;
    private final Clock clock;

    public LoyaltyService(
            LoyaltyAccountRepository accounts,
            LoyaltyAccountWriter accountWriter,
            LoyaltyTransactionRepository transactions,
            LoyaltyRuleRepository rules,
            Clock clock
    ) {
        this.accounts = accounts;
        this.accountWriter = accountWriter;
        this.transactions = transactions;
        this.rules = rules;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void award(UUID customerId, int points, String reason) {
        doAward(customerId, points, reason);
    }

    /**
     * No-op (not an error) if no active rule is configured for this event -
     * loyalty is meant to be optional/configurable, not something billing
     * should fail over.
     */
    @Transactional
    public void awardForEvent(UUID customerId, LoyaltyEventType eventType) {
        rules.findByEventTypeAndActiveTrue(eventType)
                .ifPresent(rule -> doAward(customerId, rule.getPoints(), eventType.name()));
    }

    @Transactional
    public void redeem(UUID customerId, int points, String reason) {
        LoyaltyAccount account = accounts.findByCustomerId(customerId)
                .orElseThrow(() -> new InsufficientLoyaltyPointsException(customerId, points, 0));

        account.debit(points, clock.instant());
        accounts.save(account);
        transactions.save(new LoyaltyTransaction(
                UUID.randomUUID(), account, LoyaltyTransactionType.REDEEM, points, reason, clock.instant()
        ));
        log.info("Loyalty points redeemed: customerId={}, points={}, reason={}", customerId, points, reason);
    }

    @Transactional(readOnly = true)
    public int getBalance(UUID customerId) {
        return accounts.findByCustomerId(customerId).map(LoyaltyAccount::getBalance).orElse(0);
    }

    @Transactional(readOnly = true)
    public List<LoyaltyTransaction> getHistory(UUID customerId) {
        return accounts.findByCustomerId(customerId)
                .map(account -> transactions.findByAccountIdOrderByOccurredAtDesc(account.getId()))
                .orElseGet(List::of);
    }

    /**
     * Retries through LoyaltyAccountWriter.creditOnce() (its own fresh
     * REQUIRES_NEW transaction per attempt - see its javadoc for why that,
     * not a plain loop, is what actually lets a retry see the other
     * thread's committed balance) rather than crediting inline here. The
     * catch has to live HERE, outside creditOnce()'s own transactional
     * boundary - see creditOnce()'s javadoc for why catching inside it
     * doesn't work. Five attempts is generous for two threads colliding
     * once; if it's still losing the race after that many, something is
     * wrong beyond what a retry can paper over.
     */
    private void doAward(UUID customerId, int points, String reason) {
        UUID accountId = findOrCreateAccount(customerId).getId();
        Instant now = clock.instant();

        for (int attempt = 1; attempt <= MAX_CREDIT_ATTEMPTS; attempt++) {
            try {
                accountWriter.creditOnce(accountId, points, reason, now);
                log.info("Loyalty points awarded: customerId={}, points={}, reason={}", customerId, points, reason);
                return;
            } catch (ObjectOptimisticLockingFailureException lostRace) {
                if (attempt == MAX_CREDIT_ATTEMPTS) {
                    throw lostRace;
                }
            }
        }
    }

    private LoyaltyAccount findOrCreateAccount(UUID customerId) {
        return accounts.findByCustomerId(customerId).orElseGet(() -> {
            try {
                return accountWriter.insert(new LoyaltyAccount(UUID.randomUUID(), customerId, clock.instant()));
            } catch (DataIntegrityViolationException raceLost) {
                return accounts.findByCustomerId(customerId).orElseThrow(() -> raceLost);
            }
        });
    }
}
