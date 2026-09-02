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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
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
    }

    @Transactional(readOnly = true)
    public int getBalance(UUID customerId) {
        return accounts.findByCustomerId(customerId).map(LoyaltyAccount::getBalance).orElse(0);
    }

    private void doAward(UUID customerId, int points, String reason) {
        LoyaltyAccount account = findOrCreateAccount(customerId);
        account.credit(points, clock.instant());
        accounts.save(account);
        transactions.save(new LoyaltyTransaction(
                UUID.randomUUID(), account, LoyaltyTransactionType.EARN, points, reason, clock.instant()
        ));
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
