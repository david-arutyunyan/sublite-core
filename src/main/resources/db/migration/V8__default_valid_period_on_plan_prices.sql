-- valid_period still isn't mapped on the Java side (see PlanPrice.java) -
-- proper price-period handling belongs to a later day. A DB-side default
-- lets Hibernate insert a PlanPrice without knowing about tstzrange at all:
-- "valid from now until superseded" is a reasonable default for a freshly
-- created price.
ALTER TABLE subscription.plan_prices
    ALTER COLUMN valid_period SET DEFAULT tstzrange(now(), null);
