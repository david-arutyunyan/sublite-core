-- role defaults to CUSTOMER so existing rows (and every test fixture that
-- inserts a user without thinking about auth at all) stay valid.
-- password_hash is nullable on purpose: customers don't log into this API
-- in this project, only admins do (see User.java).
ALTER TABLE shared.users
    ADD COLUMN role          VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER' CHECK (role IN ('CUSTOMER', 'ADMIN')),
    ADD COLUMN password_hash VARCHAR(255);
