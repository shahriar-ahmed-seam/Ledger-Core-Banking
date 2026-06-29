-- V3: Enforce append-only semantics at the database-role level.
-- Requirements: 5.7, 11.2, 11.4
--
-- When a dedicated least-privilege application role ('ledger_app') exists (as in the
-- docker-compose deployment), the application connects as that role and is granted only
-- SELECT/INSERT on ledger_entries and audit_log -- never UPDATE or DELETE. This makes the
-- append-only guarantee enforceable by the engine itself, independent of application code.
-- When running as a single owner role (e.g. Testcontainers), this migration is a no-op.

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'ledger_app') THEN
        -- Connection + schema usage
        GRANT USAGE ON SCHEMA public TO ledger_app;

        -- Mutable tables: full DML as required by the application.
        GRANT SELECT, INSERT, UPDATE, DELETE ON
            users, accounts, refresh_tokens, idempotency_keys, login_attempts
            TO ledger_app;

        -- Insert-only group tables.
        GRANT SELECT, INSERT ON transactions TO ledger_app;

        -- Append-only tables: SELECT + INSERT only. No UPDATE / DELETE.
        GRANT SELECT, INSERT ON ledger_entries TO ledger_app;
        GRANT SELECT, INSERT ON audit_log TO ledger_app;

        -- Identity sequences are owned by the tables; INSERT covers them. Nothing else needed.
        RAISE NOTICE 'Applied least-privilege grants to role ledger_app (append-only ledger_entries, audit_log).';
    ELSE
        RAISE NOTICE 'Role ledger_app not present; skipping least-privilege grants (single-role mode).';
    END IF;
END
$$;
