CREATE TABLE loyalty.loyalty_rules (
    id          UUID PRIMARY KEY,
    event_type  VARCHAR(30) NOT NULL CHECK (event_type IN ('PAYMENT_SUCCESS')),
    points      INT NOT NULL CHECK (points > 0),
    is_active   BOOLEAN NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- at most one ACTIVE rule per event type - same partial-unique-index
-- pattern as subscriptions (one live subscription per customer): old,
-- deactivated rules can still exist for history, but only one governs
-- new awards at a time.
CREATE UNIQUE INDEX uq_loyalty_rules_active_event_type
    ON loyalty.loyalty_rules (event_type)
    WHERE is_active;
