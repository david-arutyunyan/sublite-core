package com.sublite.loyalty.application;

import com.sublite.loyalty.domain.LoyaltyAccount;
import com.sublite.loyalty.domain.LoyaltyTransaction;
import com.sublite.loyalty.domain.LoyaltyTransactionType;
import com.sublite.loyalty.infrastructure.LoyaltyAccountRepository;
import com.sublite.loyalty.infrastructure.LoyaltyTransactionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Same pattern as billing's PaymentAttemptWriter, for the same reason: two
 * customers' first-ever award could race to create the same account (the
 * unique index on customer_id is what actually decides the winner), and
 * catching that race's constraint violation only works cleanly if the
 * failed INSERT happened in its own transaction - Postgres poisons
 * whatever transaction a failed statement ran in, so the fallback read
 * needs to run somewhere else (see LoyaltyService.findOrCreateAccount()).
 */
@Component
class LoyaltyAccountWriter {

    private final LoyaltyAccountRepository accounts;
    private final LoyaltyTransactionRepository transactions;

    LoyaltyAccountWriter(LoyaltyAccountRepository accounts, LoyaltyTransactionRepository transactions) {
        this.accounts = accounts;
        this.transactions = transactions;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LoyaltyAccount insert(LoyaltyAccount account) {
        return accounts.saveAndFlush(account);
    }

    /**
     * One credit attempt, in its own fresh transaction: REQUIRES_NEW isn't
     * just isolation here, it's what makes a retry actually see the other
     * thread's committed balance. Re-reading through the SAME transaction
     * (as a plain retry loop inside one @Transactional method would) hits
     * Hibernate's session-level identity map and hands back the exact same
     * stale, already-loaded entity instead of a fresh row - the optimistic
     * check would just fail the same way every time.
     *
     * ObjectOptimisticLockingFailureException is deliberately NOT caught
     * here - it has to propagate all the way out of this REQUIRES_NEW
     * method for LoyaltyService.doAward()'s retry loop to recover cleanly.
     * saveAndFlush() is itself a transactional repository call
     * (REQUIRED, so it participates in THIS method's transaction); the
     * moment it throws, Spring marks this transaction rollback-only right
     * then, before any catch block in this same method would even run - a
     * catch in here would still hit UnexpectedRollbackException on return
     * (found the hard way: green 5 times running this test alone, then a
     * failure the first time it ran inside the full suite). Catching
     * OUTSIDE this method's boundary, once the REQUIRES_NEW transaction
     * has already rolled back and released control back to the caller,
     * is what actually works - same shape as insert()/findOrCreateAccount()
     * just below.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void creditOnce(UUID accountId, int points, String reason, Instant now) {
        LoyaltyAccount account = accounts.findById(accountId).orElseThrow();
        account.credit(points, now);
        accounts.saveAndFlush(account);
        transactions.save(new LoyaltyTransaction(
                UUID.randomUUID(), account, LoyaltyTransactionType.EARN, points, reason, now
        ));
    }
}
