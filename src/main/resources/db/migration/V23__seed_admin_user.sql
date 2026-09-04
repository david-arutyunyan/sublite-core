-- Demo admin account so the admin API is reachable right after
-- `docker compose up` + migrate, with no separate bootstrap step.
-- Password is "admin123!" (bcrypt below) - this is a portfolio project,
-- not a real deployment; document the credential in the README, don't
-- treat it as a secret.
INSERT INTO shared.users (id, email, role, password_hash, created_at, updated_at)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'admin@sublite.dev',
    'ADMIN',
    '$2a$10$66jOoqkt7shx3ocltdzf9eMeVC5AnQs3R99/0WYTVJRTh.Y9dkRPK',
    now(),
    now()
);
