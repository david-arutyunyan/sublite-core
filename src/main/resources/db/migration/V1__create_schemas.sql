CREATE SCHEMA IF NOT EXISTS shared;
CREATE SCHEMA IF NOT EXISTS subscription;

-- needed for the EXCLUDE constraint on subscription.plan_prices (V4)
CREATE EXTENSION IF NOT EXISTS btree_gist;
