-- needed by the retry backoff calculation in RetryPolicy: "when was the
-- last time we tried to charge this subscription".
ALTER TABLE subscription.subscriptions
    ADD COLUMN last_charge_attempt_at TIMESTAMPTZ;
