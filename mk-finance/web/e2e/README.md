# E2E smoke (Playwright)

`smoke.spec.ts` drives the canonical happy path against the **real stack**
(backend + Postgres, no MSW): register → account → income entry → card →
installment card purchase → statement payment → dashboard. It is the only test
that exercises the real context-path + CSRF wiring from a browser.

## Prerequisites

The API and Postgres must be up before running:

- **Docker (closest to prod):**
  `POSTGRES_PASSWORD=dev docker compose -f ../docker/docker-compose.yml up -d postgres api`
  then point the SPA at it, or run the whole stack and set `E2E_BASE_URL`.
- **Local dev:** a Postgres on `localhost:5432` (db/user/pass `mkfinance`) and
  `./mvnw spring-boot:run` in `../api`.

The Vite dev server is started automatically by `playwright.config.ts`
(reusing one already running) with `VITE_API_MOCK=false`.

## Run

```bash
npm run test:e2e
```

Each run registers a fresh unique user, so it is safe to re-run against a
persistent database.
