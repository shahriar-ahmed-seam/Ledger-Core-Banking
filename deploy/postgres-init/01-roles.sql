-- Creates the least-privilege application login role used by the backend at runtime.
-- The default POSTGRES_USER (ledger_admin) owns the schema and runs Flyway migrations;
-- the application connects as ledger_app, which V3 grants append-only access to the ledger.
CREATE ROLE ledger_app WITH LOGIN PASSWORD 'ledger_app_pw';
GRANT CONNECT ON DATABASE ledgercore TO ledger_app;
