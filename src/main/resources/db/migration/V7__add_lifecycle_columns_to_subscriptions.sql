-- additive migration: day 1 didn't anticipate the lifecycle needing a
-- failed-attempt counter or a cancellation reason, so we add them now
-- instead of going back and editing V5.
ALTER TABLE subscription.subscriptions
    ADD COLUMN failed_charge_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN cancellation_reason VARCHAR(50);
