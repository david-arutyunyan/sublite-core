package com.sublite.loyalty.application;

import com.sublite.loyalty.domain.LoyaltyAccount;
import com.sublite.loyalty.infrastructure.LoyaltyAccountRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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

    LoyaltyAccountWriter(LoyaltyAccountRepository accounts) {
        this.accounts = accounts;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LoyaltyAccount insert(LoyaltyAccount account) {
        return accounts.saveAndFlush(account);
    }
}
