-- V2: Supporting schema for idempotency, refresh tokens, audit, and login throttling.
-- Requirements: 2.6, 2.7, 2.9, 8.1, 8.5, 11.1, 11.2

-- Idempotency keys: the PK provides the single-winner guarantee under concurrent duplicates.
-- Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 8.6
CREATE TABLE idempotency_keys (
    idempotency_key     TEXT PRIMARY KEY CHECK (char_length(idempotency_key) BETWEEN 1 AND 128),
    request_fingerprint TEXT        NOT NULL,
    status              TEXT        NOT NULL CHECK (status IN ('IN_PROGRESS', 'COMPLETED')),
    transaction_id      UUID        REFERENCES transactions (id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at        TIMESTAMPTZ
);
CREATE INDEX idx_idempotency_created ON idempotency_keys (created_at);

-- Refresh tokens: stored as hashes, revocable for logout/invalidation.
-- Requirements: 2.6, 2.7, 2.8
CREATE TABLE refresh_tokens (
    jti        UUID PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id),
    token_hash TEXT        NOT NULL,
    revoked    BOOLEAN     NOT NULL DEFAULT FALSE,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_user ON refresh_tokens (user_id);

-- Audit log: append-only, deterministic ordering by (occurred_at, id).
-- Requirements: 11.1, 11.2, 11.3
CREATE TABLE audit_log (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    actor_id    UUID,
    action_type TEXT        NOT NULL,
    target_id   TEXT,
    outcome     TEXT        NOT NULL CHECK (outcome IN ('SUCCESS', 'FAILURE')),
    detail      TEXT,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX idx_audit_occurred ON audit_log (occurred_at, id);

-- Login attempts: sliding-window record per normalized email for lockout.
-- Requirement 2.9
CREATE TABLE login_attempts (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email_normalized TEXT        NOT NULL,
    success          BOOLEAN     NOT NULL,
    attempted_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_login_attempts_email_time ON login_attempts (email_normalized, attempted_at);
