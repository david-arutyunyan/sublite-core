CREATE TABLE shared.users (
    id            UUID PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- lower(email) instead of a plain UNIQUE(email): "Bob@x.com" and "bob@x.com"
-- must not be two different accounts.
CREATE UNIQUE INDEX uq_users_email ON shared.users (lower(email));
