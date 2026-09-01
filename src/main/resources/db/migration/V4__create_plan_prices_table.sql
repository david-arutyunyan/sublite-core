CREATE TABLE subscription.plan_prices (
    id              UUID PRIMARY KEY,
    plan_id         UUID NOT NULL REFERENCES subscription.plans(id),
    billing_period  VARCHAR(10) NOT NULL CHECK (billing_period IN ('MONTHLY', 'YEARLY')),
    amount          NUMERIC(10, 2) NOT NULL CHECK (amount >= 0),
    currency        VARCHAR(3) NOT NULL,

    -- [valid_from, valid_to) — valid_to = infinity while this is the current price.
    -- See V4 note in the migration explanation for why this is a range, not two columns.
    valid_period    TSTZRANGE NOT NULL,

    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- Postgres itself refuses to insert a price whose valid_period overlaps
    -- an existing price for the same plan + billing_period.
    CONSTRAINT excl_plan_prices_overlap EXCLUDE USING gist (
        plan_id WITH =,
        billing_period WITH =,
        valid_period WITH &&
    )
);

CREATE INDEX idx_plan_prices_plan_id ON subscription.plan_prices (plan_id);
