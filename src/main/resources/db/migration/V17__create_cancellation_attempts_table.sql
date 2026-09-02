-- subscription_id DOES have a real FK here, unlike billing.invoices:
-- retention isn't a module we plan to cut into its own service (only
-- billing is, per the follow-up Sublite Distributed project), so
-- referential integrity wins over rehearsing a service boundary.
CREATE TABLE retention.cancellation_attempts (
    id                 UUID PRIMARY KEY,
    subscription_id    UUID NOT NULL REFERENCES subscription.subscriptions(id),
    status             VARCHAR(20) NOT NULL CHECK (status IN ('IN_PROGRESS', 'RETAINED', 'CANCELLED')),
    current_step_order INT NOT NULL,
    reason             VARCHAR(255),
    accepted_offer_id  UUID REFERENCES retention.retention_offers(id),
    started_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at       TIMESTAMPTZ
);

-- analytics query this table exists for: "where do people bail, and which
-- offers actually work" - both filter by status, so index it.
CREATE INDEX idx_cancellation_attempts_status ON retention.cancellation_attempts (status);
CREATE INDEX idx_cancellation_attempts_subscription ON retention.cancellation_attempts (subscription_id);
