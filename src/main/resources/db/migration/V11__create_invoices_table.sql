-- subscription_id is a plain UUID with NO foreign key to
-- subscription.subscriptions: unlike shared<->subscription (which will
-- always live in one process), billing is the module most likely to be
-- cut out into its own service later (see the Sublite Distributed
-- follow-up project) - so it's treated as if it already talks to
-- subscription only through an API, never through a shared DB constraint.
CREATE TABLE billing.invoices (
    id               UUID PRIMARY KEY,
    subscription_id  UUID NOT NULL,
    period_start     TIMESTAMPTZ NOT NULL,
    period_end       TIMESTAMPTZ NOT NULL,
    amount           NUMERIC(10, 2) NOT NULL CHECK (amount >= 0),
    currency         VARCHAR(3) NOT NULL,
    status           VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'PAID', 'FAILED')),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- one invoice per subscription per billing period - the scheduler relies
-- on this to be safely re-run without creating a duplicate bill.
CREATE UNIQUE INDEX uq_invoices_subscription_period
    ON billing.invoices (subscription_id, period_start);

CREATE INDEX idx_invoices_status ON billing.invoices (status);
