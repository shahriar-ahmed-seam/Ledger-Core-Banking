# Ledger-Core-Banking

> A production-grade core banking engine with double-entry bookkeeping, concurrency-safe money movement, and a premium internet-banking dashboard.

[![Backend](https://img.shields.io/badge/backend-Java%2021%20%C2%B7%20Spring%20Boot%203.4-006a4e)]()
[![Database](https://img.shields.io/badge/database-PostgreSQL%2016-336791)]()
[![Frontend](https://img.shields.io/badge/frontend-React%2018%20%C2%B7%20TypeScript%20%C2%B7%20Vite-1d6fa5)]()
[![Tests](https://img.shields.io/badge/tests-41%20properties%20%2B%20integration-0a7d4d)]()
[![License](https://img.shields.io/badge/license-MIT-555)]()

Ledger-Core-Banking is a simplified but rigorously correct banking backend. Its defining
goal is **monetary integrity**: every financial event is recorded as a balanced
double-entry transaction, account balances are always derivable from an append-only ledger,
and concurrent balance changes on the same account are serialized so that **no money is ever
lost or created**.

---

## Table of Contents

- [Highlights](#highlights)
- [Architecture](#architecture)
- [The hard parts](#the-hard-parts)
- [Tech stack](#tech-stack)
- [Getting started](#getting-started)
- [Testing](#testing)
- [API overview](#api-overview)
- [Project structure](#project-structure)
- [Security](#security)
- [Roadmap](#roadmap)
- [License](#license)

---

## Highlights

- **Double-entry bookkeeping** — every transaction has ≥1 debit and ≥1 credit, debits equal
  credits, and the ledger is append-only (enforced at the database-role level by revoking
  `UPDATE`/`DELETE`). Corrections are reversing transactions, never edits.
- **Concurrency-safe transfers** — PostgreSQL row locks (`SELECT … FOR UPDATE`) acquired in
  deterministic ascending account-id order (deadlock-free) and held until commit, with a
  configurable lock-wait timeout that surfaces a retryable error.
- **Idempotent money movement** — a primary-key single-winner guarantee plus request
  fingerprinting ensures retries never move money twice.
- **ACID throughout** — all entries of a transaction commit or none do; balances are never
  left partial or corrupt, verified across simulated crashes.
- **Security** — Argon2id password hashing, RS256 JWT access tokens, revocable refresh
  tokens, brute-force lockout, enumeration-resistant login, and role-based access control
  (CUSTOMER / TELLER / ADMIN).
- **Immutable audit trail** — written in the same transaction as the action it records.
- **Premium dashboard** — a retail internet-banking UI (landing page, sign-in, account
  summary, fund transfer, statements) built with a hand-crafted design system.

## Architecture

A layered modular monolith. The correctness-critical ledger and concurrency logic lives in
a thin, heavily tested core that has no dependency on HTTP or UI concerns.

```
React/TS Dashboard ──HTTPS+JWT──▶ Spring MVC (filters, RBAC, validation, error envelope)
                                        │
                                        ▼
        Auth · Authz · Account · Ledger · Transfer · Idempotency · Audit  (domain services)
                                        │
                                        ▼
                Spring Data JPA (pessimistic locking) ──▶ PostgreSQL 16
```

Balance-changing operations run at **READ COMMITTED** with **explicit pessimistic row
locks**, which prevents lost updates while keeping throughput high. See
[`.kiro/specs/ledger-core-banking/design.md`](.kiro/specs/ledger-core-banking/design.md)
for the full design, including the funds-transfer & locking algorithm and 41 correctness
properties.

## The hard parts

| Concern | Where | How |
|---------|-------|-----|
| Books always balance | `LedgerService.postTransaction` | Validates debits == credits and ≥1 of each before any write |
| Append-only ledger | DB grants + repository surface | `UPDATE`/`DELETE` revoked from the app role; repositories expose insert + read only |
| No lost/duplicated money | `TransferService` | `FOR UPDATE` locks in ascending id order, balance re-read under lock, overdraft checked |
| Deadlock avoidance | `LockOrdering` | Global ascending lock order; lock-wait timeout → retryable error |
| Exactly-once transfers | `IdempotencyService` | Idempotency-key PK single-winner + request fingerprint |
| Exact money | `Money` (`BigDecimal`) | Fixed-precision decimals end to end — never binary floating point |

## Tech stack

| Layer | Technology |
|-------|-----------|
| Backend | Java 21, Spring Boot 3.4, Spring Security, Spring Data JPA |
| Database | PostgreSQL 16, Flyway migrations |
| Auth | Argon2id, RS256 JWT (jjwt), RBAC |
| Frontend | React 18, TypeScript, Vite, TanStack Query, React Hook Form, Zod |
| Testing | jqwik (property-based) + Testcontainers (backend); fast-check + Vitest (frontend) |

## Getting started

### Prerequisites
- JDK 21+, Docker (for PostgreSQL / Testcontainers), Node.js 20+.

### Run everything with Docker Compose
```bash
docker compose up --build
```
- Dashboard: http://localhost:5173
- API: http://localhost:8080
- Seeded demo logins: `demo@ledger.local / DemoPass1234!` and `admin@ledger.local / AdminPass123!`

### Run services individually
```bash
# Backend (needs a PostgreSQL; override with DB_URL / DB_USERNAME / DB_PASSWORD)
cd backend && ./gradlew bootRun

# Frontend (proxies /api to the backend)
cd frontend && npm install && npm run dev
```

## Testing

```bash
cd backend && ./gradlew test     # property-based + Testcontainers integration (needs Docker)
cd frontend && npm test          # property-based + component
```

The design defines **41 correctness properties**. Properties 1–39 are verified in the
backend with jqwik (≥100 iterations each); properties 40–41 in the frontend with fast-check.
Concurrency, append-only enforcement, durability, ordering, and pagination are additionally
verified against a real PostgreSQL instance via Testcontainers. Each property test is tagged
with the requirement it validates.

## API overview

| Method & path | Purpose |
|---------------|---------|
| `POST /api/v1/auth/register` | Register a customer |
| `POST /api/v1/auth/login` | Authenticate, receive tokens |
| `POST /api/v1/auth/refresh` | Exchange a refresh token for a new access token |
| `POST /api/v1/auth/logout` | Invalidate a refresh token |
| `POST /api/v1/accounts` | Open an account |
| `GET /api/v1/accounts` | List the caller's accounts |
| `GET /api/v1/accounts/{id}` | View an account |
| `POST /api/v1/accounts/{id}/close` | Close a zero-balance account |
| `POST /api/v1/transfers` | Transfer funds (requires `Idempotency-Key`) |
| `GET /api/v1/accounts/{id}/entries` | Ledger entries (paginated) |
| `GET /api/v1/accounts/{id}/statement` | Statement with running balance |
| `PUT /api/v1/users/{id}/role` | Change a user's role (ADMIN) |
| `GET /api/v1/audit` | Query the audit trail (ADMIN) |

All money is transported as decimal strings with an explicit ISO 4217 currency. Errors use a
uniform envelope: `{ "error": { "code", "message", "fields" } }`.

## Project structure

```
Ledger-Core-Banking/
├── backend/                 # Spring Boot service
│   └── src/main/java/com/ledgercore/
│       ├── account/  audit/  auth/  authz/  common/  config/
│       ├── idempotency/  ledger/  security/  transfer/  user/  api/
├── frontend/                # React + TypeScript dashboard
│   └── src/{components, features, lib, styles}
├── deploy/postgres-init/    # least-privilege DB role bootstrap
├── docker-compose.yml
└── .kiro/specs/ledger-core-banking/   # requirements, design, tasks
```

## Security

- Access tokens expire in 15 minutes; refresh tokens in 7 days and are revocable on logout.
- Login is enumeration-resistant (constant-time comparison) and rate-limited (5 failures /
  15-minute window).
- The append-only ledger and audit log are enforced by least-privilege database grants — the
  application connects as a role without `UPDATE`/`DELETE` on those tables.
- Monetary values never use binary floating point.

**Production notes:** replace the dev-generated RSA signing key with a managed key store,
terminate TLS in front of the service, and run the backend as the least-privilege
`ledger_app` role (already wired in `docker-compose.yml`).

## Roadmap

- Statement export (PDF/CSV) and scheduled transfers
- Multi-currency FX transfers with rate capture
- OpenAPI/Swagger documentation and a typed client
- Observability (metrics, tracing) and CI pipeline

## License

Released under the MIT License. See [`LICENSE`](LICENSE).
