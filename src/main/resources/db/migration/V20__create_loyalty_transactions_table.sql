-- append-only ledger: never updated or deleted, same pattern as
-- subscription.subscription_status_history. balance on loyalty_accounts
-- is a denormalized running total kept in sync in the same transaction
-- as each insert here - fast reads without summing the ledger every time,
-- while this table stays the auditable source of truth for how it got there.
CREATE TABLE loyalty.loyalty_transactions (
    id          UUID PRIMARY KEY,
    account_id  UUID NOT NULL REFERENCES loyalty.loyalty_accounts(id),
    type        VARCHAR(10) NOT NULL CHECK (type IN ('EARN', 'REDEEM')),
    points      INT NOT NULL CHECK (points > 0),
    reason      VARCHAR(100) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_loyalty_transactions_account ON loyalty.loyalty_transactions (account_id, occurred_at);
