-- required table for ShedLock's JDBC lock provider. Lives in "shared"
-- rather than "billing" because it's a cross-cutting technical concern
-- (any future scheduled job in any module can use it), not billing data.
CREATE TABLE shared.shedlock (
    name       VARCHAR(64) NOT NULL PRIMARY KEY,
    lock_until TIMESTAMPTZ NOT NULL,
    locked_at  TIMESTAMPTZ NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);
