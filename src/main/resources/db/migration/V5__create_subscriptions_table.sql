CREATE TABLE subscription.subscriptions (
    id                     UUID PRIMARY KEY,
    customer_id            UUID NOT NULL REFERENCES shared.users(id),
    plan_price_id          UUID NOT NULL REFERENCES subscription.plan_prices(id),
    status                 VARCHAR(20) NOT NULL
                               CHECK (status IN ('TRIAL', 'ACTIVE', 'GRACE_PERIOD', 'PAUSED', 'CANCELLED')),
    trial_ends_at          TIMESTAMPTZ,
    current_period_start   TIMESTAMPTZ NOT NULL,
    current_period_end     TIMESTAMPTZ NOT NULL,
    cancel_at_period_end   BOOLEAN NOT NULL DEFAULT false,
    cancelled_at           TIMESTAMPTZ,

    -- optimistic locking (JPA @Version): protects against the billing scheduler
    -- and a user's cancel request racing on the same row.
    version                BIGINT NOT NULL DEFAULT 0,

    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- business rule "one live subscription per customer", enforced by the DB,
-- not just in application code.
CREATE UNIQUE INDEX uq_subscriptions_customer_active
    ON subscription.subscriptions (customer_id)
    WHERE status IN ('TRIAL', 'ACTIVE', 'GRACE_PERIOD', 'PAUSED');

-- supports the future billing scheduler query:
-- WHERE status = 'ACTIVE' AND current_period_end < now()
CREATE INDEX idx_subscriptions_status_period_end
    ON subscription.subscriptions (status, current_period_end);
