# Deployment Guide

This project deploys as three managed pieces:

| Component | Platform | What it runs |
|-----------|----------|--------------|
| Database | **Neon** | Serverless PostgreSQL 16 |
| Backend API | **Render** | Spring Boot service (Docker) |
| Frontend | **Vercel** | React/Vite static site, proxying `/api` to Render |

The frontend calls the backend through a same-origin Vercel rewrite (`/api/* → Render`), so
there is no cross-origin/CORS complexity and the browser only ever talks to the Vercel domain.

---

## 1. Database — Neon

1. Create a project at https://neon.tech and a database named `ledgercore`.
2. From **Connection Details**, copy the host, role (user), and password.
3. Build the JDBC URL (note `sslmode=require`):
   ```
   jdbc:postgresql://<your-host>.neon.tech/ledgercore?sslmode=require
   ```
   Keep the username and password handy for Render.

> Single-role note: on Neon the app connects as the database owner, so the optional
> least-privilege `ledger_app` grants (migration `V3`) are skipped automatically. The
> append-only guarantee for the ledger and audit log is still enforced in the application
> layer (repositories expose insert + read only).

## 2. Backend — Render

The repository ships a Render Blueprint (`render.yaml`) and a `backend/Dockerfile`.

1. Push this repo to GitHub (already done).
2. In Render: **New → Blueprint**, select the repo. Render reads `render.yaml` and creates the
   `ledger-core-banking-api` web service.
3. Set the service environment variables (marked `sync: false`):
   - `DB_URL` → the Neon JDBC URL from step 1
   - `DB_USERNAME` → Neon role
   - `DB_PASSWORD` → Neon password
   - `CORS_ORIGINS` → your Vercel URL (e.g. `https://ledger-core-banking.vercel.app`)
   - `SEED_ENABLED` → `true` for the first deploy to create demo data, then change to `false`
     and redeploy.
4. Deploy. Render builds the Docker image, Flyway runs the migrations on first boot, and the
   health check hits `/actuator/health`.
5. Copy the service URL, e.g. `https://ledger-core-banking-api.onrender.com`.

> Free plan: the service sleeps after inactivity, so the first request after idle can take
> ~30–60s (cold start). Neon free compute also auto-suspends similarly.

## 3. Frontend — Vercel

1. Edit `frontend/vercel.json` and replace `REPLACE-WITH-RENDER-HOST.onrender.com` with your
   actual Render host from step 2 (keep the `https://` and `/api/:path*`).
2. In Vercel: **Add New → Project**, import the repo, and set the **Root Directory** to
   `frontend`. The framework (Vite), build command, and output directory are picked up from
   `vercel.json`.
3. Deploy. Vercel serves the SPA and rewrites `/api/*` to the Render backend.
4. Open the Vercel URL and sign in.

If you changed the Vercel domain, update `CORS_ORIGINS` on Render to match (only needed if you
ever switch the frontend to call the backend directly instead of via the rewrite).

---

## Local development

```bash
docker compose up --build      # full stack on :5173 (web), :8080 (api), :5432 (db)
```

See [`README.md`](README.md) for running services individually and for testing.

## Environment variable reference (backend)

| Variable | Required | Description |
|----------|----------|-------------|
| `DB_URL` | yes | JDBC URL to PostgreSQL (`sslmode=require` for Neon) |
| `DB_USERNAME` / `DB_PASSWORD` | yes | Database credentials |
| `PORT` | auto | Injected by Render; the app binds to it |
| `CORS_ORIGINS` | recommended | Allowed browser origin(s), comma-separated |
| `SEED_ENABLED` | no | `true` seeds a demo admin + customer once (default `false`) |
