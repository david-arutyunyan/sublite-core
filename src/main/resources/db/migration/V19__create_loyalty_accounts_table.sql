-- FK to shared.users, same reasoning as retention.cancellation_attempts:
-- loyalty isn't a module planned for extraction into its own service.
CREATE TABLE loyalty.loyalty_accounts (
    id          UUID PRIMARY KEY,
    customer_id UUID NOT NULL REFERENCES shared.users(id),
    balance     INT NOT NULL DEFAULT 0 CHECK (balance >= 0),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- one account per customer - also what LoyaltyAccountWriter's
-- find-or-create race protection relies on.
CREATE UNIQUE INDEX uq_loyalty_accounts_customer ON loyalty.loyalty_accounts (customer_id);
