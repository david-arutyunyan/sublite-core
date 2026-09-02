CREATE TABLE retention.retention_steps (
    id          UUID PRIMARY KEY,
    step_order  INT NOT NULL,
    type        VARCHAR(20) NOT NULL CHECK (type IN ('SURVEY', 'OFFER', 'CONFIRMATION')),
    offer_id    UUID REFERENCES retention.retention_offers(id),
    is_active   BOOLEAN NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- one flow for now (no multi-variant/A-B testing), so step order must be
-- unique across the whole table, not per-flow.
CREATE UNIQUE INDEX uq_retention_steps_order ON retention.retention_steps (step_order);
