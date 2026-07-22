# Investment Acquisition Cost Tracker (IR) — Backend

Spring Boot 3.5 / Java 21 backend for the acquisition-cost and capital-gains (IR) tracker.
It is the single source of truth for average-price, ledger and tax calculations that the
React front-end in `../app` currently mocks client-side.

## Stack

- Java 21, Spring Boot 3.5 (Web MVC, Data JPA, Validation, Actuator)
- PostgreSQL 16 + Flyway migrations (`src/main/resources/db/migration`)
- Real PTAX rates from the Banco Central Olinda API, with a persisted read-through cache
- springdoc-openapi (Swagger UI), Testcontainers + WireMock for integration tests

## Running

Requirements: Java 21 and Docker.

```bash
docker compose up -d      # PostgreSQL 16 on localhost:5432 (db/user/password: ir)
./mvnw spring-boot:run    # http://localhost:8080  (spring-boot-docker-compose can also auto-start the db)
```

Swagger UI: <http://localhost:8080/swagger-ui.html>

The database is migrated and seeded automatically (default user, 8 assets, 19 orders —
the same data set as the front-end mock).

## API overview (`/api/v1`)

| Endpoint | Purpose |
|---|---|
| `GET/POST /assets` | List / add portfolio assets |
| `GET/POST /orders`, `PATCH/DELETE /orders/{id}` | Order CRUD; edits/deletes revalidate the whole history |
| `GET /ledger?ticker=` | Full computed ledger (dual USD/BRL columns for US assets) |
| `GET /positions?ticker=&date=` | Consolidated position, optional cutoff date (inclusive) |
| `GET /capital-gains?year=`, `GET /capital-gains/years` | Yearly capital-gains report grouped by month |
| `GET /dashboard?referenceDate=` | Positions today + at reference date, BRL totals Brazil/abroad |
| `GET /ptax?date=&type=buy\|sell` | PTAX rate with next-business-day fallback |

Errors follow RFC 7807 (`application/problem+json`) with a `code` extension
(e.g. `INSUFFICIENT_QUANTITY`, `PTAX_UNAVAILABLE`).

## Business rules

Ported 1:1 from the front-end (`app/src/lib/calc.ts`), per order type: weighted-average
price on BUY, dilution on BONUS_SHARES, cost-preserving SPLIT/REVERSE_SPLIT,
average-price reduction on AMORTIZATION, and unchanged average on SELL with
gain = quantity × (price-per-share − average price). Tax: 15% (BR stocks/ETFs/units and
all US assets) or 20% (FII), charged only on profitable sales, always on the
BRL-denominated gain. US assets keep an independent BRL ledger converted order by order
at each order's own PTAX rate (BUY → PTAX sell rate; everything else → PTAX buy rate).

**Known simplification** (kept consistent with the front-end, documented future work):
no loss compensation across operations/months and no R$ 20k/month exemption for BR
stock sales.

## Tests

```bash
./mvnw verify   # unit + integration tests (needs Docker), JaCoCo gate ≥85% on domain/application
```

Integration tests run against a real PostgreSQL (Testcontainers) and a WireMock-stubbed
BCB API, pinning the same PTAX rates the front-end mock used, so ledger expectations are
byte-exact.

## Connecting the front-end

CORS already allows `http://localhost:5173`. Point the React app at
`http://localhost:8080/api/v1` (e.g. a Vite proxy for `/api`), and rename its
Portuguese field names to this API's English contract.
