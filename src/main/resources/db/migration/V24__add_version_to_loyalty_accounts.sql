-- Closes a real lost-update race: two concurrent credit()/debit() calls
-- for the same account both read balance=0, both compute balance=100,
-- last save() wins and silently drops the other's points. subscriptions
-- already guards the equivalent race with @Version (V5); loyalty_accounts
-- never got the same protection. Without a version check, Hibernate has
-- no way to notice the second save() is based on stale data - it just
-- overwrites.
ALTER TABLE loyalty.loyalty_accounts
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
