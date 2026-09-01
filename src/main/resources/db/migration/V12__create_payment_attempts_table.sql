CREATE TABLE billing.payment_attempts (
    id               UUID PRIMARY KEY,
    invoice_id       UUID NOT NULL REFERENCES billing.invoices(id),
    idempotency_key  UUID NOT NULL,
    status           VARCHAR(20) NOT NULL CHECK (status IN ('SUCCEEDED', 'DECLINED', 'PROVIDER_ERROR')),
    failure_reason   VARCHAR(255),
    attempted_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- the actual idempotency guard: a second insert with the same key fails
-- with a constraint violation instead of silently creating a second attempt.
CREATE UNIQUE INDEX uq_payment_attempts_idempotency_key
    ON billing.payment_attempts (idempotency_key);

CREATE INDEX idx_payment_attempts_invoice ON billing.payment_attempts (invoice_id);
