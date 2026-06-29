-- V1: Core schema for Ledger-Core-Banking
-- Covers users, accounts, transactions, and the append-only ledger.
-- Requirements: 4.1, 4.2, 5.7, 5.9, 10.1

-- Users and roles (Requirement 1, 3.1)
CREATE TABLE users (
    id               UUID PRIMARY KEY,
    email_normalized TEXT        NOT NULL UNIQUE,
    password_hash    TEXT        NOT NULL,
    role             TEXT        NOT NULL CHECK (role IN ('CUSTOMER', 'TELLER', 'ADMIN')),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Accounts (Requirement 4)
CREATE TABLE accounts (
    id              UUID PRIMARY KEY,
    owner_id        UUID         NOT NULL REFERENCES users (id),
    currency        CHAR(3)      NOT NULL,
    status          TEXT         NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'CLOSED')),
    overdraft_limit NUMERIC(38, 4) NOT NULL DEFAULT 0 CHECK (overdraft_limit >= 0),
    -- Derived/reconcilable cache; the authoritative balance is the sum over ledger_entries.
    balance_cache   NUMERIC(38, 4) NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_accounts_owner ON accounts (owner_id);

-- Transactions: a balanced group of >= 2 ledger entries (Requirement 5.1, 5.8)
CREATE TABLE transactions (
    id              UUID PRIMARY KEY,
    currency        CHAR(3)      NOT NULL,
    reverses_txn_id UUID         REFERENCES transactions (id),
    reference       TEXT,
    posted_at       TIMESTAMPTZ  NOT NULL DEFAULT clock_timestamp()
);

-- Ledger entries: append-only, strictly positive amounts, direction carries the sign.
-- Requirements: 5.7, 5.9
CREATE TABLE ledger_entries (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    transaction_id UUID         NOT NULL REFERENCES transactions (id),
    account_id     UUID         NOT NULL REFERENCES accounts (id),
    direction      TEXT         NOT NULL CHECK (direction IN ('DEBIT', 'CREDIT')),
    amount         NUMERIC(38, 4) NOT NULL CHECK (amount > 0),
    currency       CHAR(3)      NOT NULL,
    posted_at      TIMESTAMPTZ  NOT NULL
);

-- Serves ordered queries, running-balance statements, and balance derivation.
-- Requirements: 10.1, 10.3, 4.4
CREATE INDEX idx_ledger_account_posted ON ledger_entries (account_id, posted_at, id);
CREATE INDEX idx_ledger_txn ON ledger_entries (transaction_id);
