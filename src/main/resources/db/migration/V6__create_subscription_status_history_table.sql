CREATE TABLE subscription.subscription_status_history (
    id               UUID PRIMARY KEY,
    subscription_id  UUID NOT NULL REFERENCES subscription.subscriptions(id),
    from_status      VARCHAR(20),
    to_status        VARCHAR(20) NOT NULL,
    reason           VARCHAR(50),
    metadata         JSONB,
    occurred_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_status_history_subscription
    ON subscription.subscription_status_history (subscription_id, occurred_at);
