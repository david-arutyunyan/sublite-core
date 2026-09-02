-- parameters is JSONB rather than typed columns: every offer type needs a
-- different shape (discount wants percent + periods, loyalty wants a point
-- amount, pause needs nothing at all) and there's no shared column set
-- that fits all three without most rows being mostly NULL.
CREATE TABLE retention.retention_offers (
    id          UUID PRIMARY KEY,
    code        VARCHAR(50) NOT NULL,
    type        VARCHAR(30) NOT NULL CHECK (type IN ('DISCOUNT_PERCENT', 'PAUSE_SUBSCRIPTION', 'LOYALTY_POINTS')),
    parameters  JSONB NOT NULL DEFAULT '{}',
    is_active   BOOLEAN NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_retention_offers_code ON retention.retention_offers (code);
