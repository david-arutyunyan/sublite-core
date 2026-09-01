CREATE TABLE subscription.plans (
    id            UUID PRIMARY KEY,
    code          VARCHAR(50)  NOT NULL,
    name          VARCHAR(255) NOT NULL,
    description   TEXT,
    is_active     BOOLEAN      NOT NULL DEFAULT true,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_plans_code ON subscription.plans (code);
