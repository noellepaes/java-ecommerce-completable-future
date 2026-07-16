# Java Ecommerce — CompletableFuture Performance Study

Modular monolith e-commerce API built with **Java 17** and **Spring Boot 3.2**, designed to measure and improve request latency by introducing **`CompletableFuture`** for concurrent work.

This repository documents the baseline (synchronous) architecture, load-testing setup with **k6**, and a clear place to compare metrics **before** and **after** async orchestration.

Repository: [noellepaes/java-ecommerce-completable-future](https://github.com/noellepaes/java-ecommerce-completable-future)

---

## What this project does

The application is a **modular monolith** organized by DDD Bounded Contexts (packages), ready to evolve into microservices later:

| Module | Responsibility |
|--------|----------------|
| `product` | Catalog, stock, product CRUD |
| `customer` | Customers |
| `order` | Orders, items, pay/cancel flow |
| `payment` | Payment processing |
| `auth` | Login and user listing |
| `recommendation` | Collaborative recommendations (Redis graph of product views) |
| `shared` | Cross-cutting types only (`BaseEntity`, domain events, exceptions) |

Each module follows a DDD layout: `domain` → `application` (use cases) → `infrastructure` → `presentation` (REST).

### Architecture highlights

- **Single executable**: one Maven/Spring Boot app (`EcommerceApplication`)
- **PostgreSQL** with separate schemas per context (`product_schema`, `customer_schema`, `order_schema`, `payment_schema`, plus auth tables)
- **Flyway** as the source of truth for schema migrations
- **Redis** for recommendation / co-view graphs
- **References by UUID** between modules (loose coupling, microservice-ready)
- **Prometheus + Grafana** for runtime metrics during load tests
- **k6** scripts for endpoint and checkout-flow load testing

### Goal of this repo (CompletableFuture)

Many flows touch multiple stores or steps (e.g. checkout, recommendations). Today they run **sequentially**. The next step is to introduce **`CompletableFuture`** (and, where useful, dedicated executors) so independent I/O can run **in parallel**, then compare:

1. **Baseline** — current synchronous implementation  
2. **Optimized** — same endpoints after `CompletableFuture` composition  

Fill in the [Load test results](#load-test-results-before--after-completablefuture) section below after each run.

---

## Tech stack

- Java 17  
- Spring Boot 3.2  
- Spring Data JPA + Validation  
- Spring Data Redis  
- SpringDoc OpenAPI (Swagger UI)  
- PostgreSQL 15  
- Flyway  
- Redis 7  
- Micrometer + Prometheus + Grafana  
- Docker Compose  
- k6  

---

## Project structure

```
src/main/java/com/ecommerce/
 ├── auth/
 ├── product/
 ├── customer/
 ├── order/
 ├── payment/
 ├── recommendation/
 ├── config/
 └── shared/

load-tests/
 ├── k6/scenarios/     # one script per endpoint (+ checkout + parallel suite)
 ├── run-suite.ps1     # sequential suite (compare endpoints in the terminal)
 └── run-all.ps1      # parallel load (visualize in Grafana)

monitoring/           # Prometheus + Grafana dashboards
docs/                 # Extra architecture notes (Portuguese)
```

---

## Quick start

### Prerequisites

- Docker & Docker Compose  
- JDK 17+ and Maven (or use the Docker `app` service)  
- [k6](https://k6.io/) if you run scripts outside Compose  

### Run with Docker Compose

```bash
docker compose up -d --build
```

Services:

| Service | URL |
|---------|-----|
| API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3000 (admin / admin) |
| PostgreSQL | localhost:5432 |
| Redis | localhost:6379 |

### Run the API locally (infra in Docker)

```bash
docker compose up -d postgres redis
mvn spring-boot:run
```

---

## Main API endpoints

### Auth
- `POST /api/auth/login`
- `GET /api/auth/users`

### Products
- `POST /api/products` — create  
- `GET /api/products` — list  
- `GET /api/products/{id}` — get by id  
- `PUT /api/products/{id}` — update  
- `POST /api/products/{id}/decrease-stock` — decrease stock  

### Customers
- `POST /api/customers` — create  
- `GET /api/customers` — list  
- `GET /api/customers/{id}` — get by id  
- `PUT /api/customers/{id}` — update  
- `DELETE /api/customers/{id}` — deactivate  

### Orders
- `POST /api/orders` — create  
- `GET /api/orders/{id}` — get by id  
- `GET /api/orders/customer/{customerId}` — list by customer  
- `POST /api/orders/{id}/items` — add item  
- `POST /api/orders/{id}/pay` — mark as paid  
- `POST /api/orders/{id}/cancel` — cancel  

### Payments
- `POST /api/payments` — process payment  
- `GET /api/payments/{id}` — get by id  
- `GET /api/payments/order/{orderId}` — list by order  

### Recommendations (Redis)
- `GET /api/recommendations/customers/{customerId}`  
- `POST /api/recommendations/customers/{customerId}/views`  

---

## Load testing with k6

Scripts live under `load-tests/k6/scenarios/`. You can run them via Docker Compose (`load-test` profile).

### Sequential suite (one endpoint at a time)

Useful for a terminal comparison table (RPS, p95, failures):

```powershell
cd load-tests
.\run-suite.ps1 -Vus 50 -Duration "30s"
```

Reports are written to `load-tests/results/`.

### Parallel suite (all endpoints together)

Useful to watch Grafana while the system is under mixed load:

```powershell
cd load-tests
.\run-all.ps1 -VusPerScenario 10 -Duration "30s"
```

Suggested Grafana dashboard:  
http://localhost:3000/d/ecommerce-load-test  
(set time range to *Last 15 minutes*, refresh every 5s)

Covered scenarios include auth, products, customers, orders, payments, Redis recommendations, and a full **checkout flow** (create order → add item → pay).

---

## Load test results: before & after CompletableFuture

> **How to use this section**  
> 1. Run the same k6 suite with the **same VUs and duration**.  
> 2. Paste the summary metrics below.  
> 3. Optionally attach Grafana screenshots under `docs/screenshots/` and link them here.

### Test conditions

| Field | Value |
|-------|--------|
| Date | 2026-07-15 |
| Branch / commit (before) | `main` @ `c32bbde` |
| Branch / commit (after) | local worktree (CompletableFuture + `open-in-view: false`) |
| VUs | 50 |
| Duration | 30s per endpoint |
| Script | `load-tests/run-suite.ps1` (before); optimized endpoints re-run after CF |
| Environment | Docker Compose on local machine (`app` + Postgres + Redis) |
| Raw report | `load-tests/results/suite-20260715-143408.txt` (+ re-run of add-item) |

---

### BEFORE — synchronous baseline (no CompletableFuture)

Measured on 2026-07-15 with the current synchronous implementation (no `CompletableFuture` yet).

#### Summary

| Metric | Value |
|--------|--------|
| Total requests (all scenarios) | 286,055 |
| Requests/s (avg) | ~128–1,367 RPS depending on endpoint (see table) |
| http_req_duration p95 | Best ~3.2 ms (reads); worst **470.31 ms** (`POST /api/auth/login`) |
| http_req_failed | **0.00%** (after fixing the add-item scenario) |
| Checks passed | **100%** on all 14 scenarios |

#### Per endpoint

| Module | Endpoint | Reqs | RPS | p95 | Failures | Checks |
|--------|----------|------|-----|-----|----------|--------|
| Auth | `POST /api/auth/login` | 3,878 | 127.72 | 470.31 ms | 0.00% | 100.00% |
| Auth | `GET /api/auth/users` | 14,500 | 482.50 | 5.01 ms | 0.00% | 100.00% |
| Product | `GET /api/products` | 14,650 | 487.48 | 3.41 ms | 0.00% | 100.00% |
| Product | `GET /api/products/{id}` | 14,652 | 486.81 | 3.38 ms | 0.00% | 100.00% |
| Customer | `GET /api/customers` | 14,650 | 487.99 | 3.42 ms | 0.00% | 100.00% |
| Customer | `GET /api/customers/{id}` | 14,680 | 486.70 | 3.23 ms | 0.00% | 100.00% |
| Order | `GET /api/orders/customer/{id}` | 14,652 | 487.12 | 3.37 ms | 0.00% | 100.00% |
| Order | `POST /api/orders` | 14,452 | 479.25 | 6.61 ms | 0.00% | 100.00% |
| Order | `POST /api/orders/{id}/items` | 27,960 | 929.01 | 5.51 ms | 0.00% | 100.00% |
| Order | `POST /api/orders/{id}/pay` | 40,865 | 1,353.06 | 5.67 ms | 0.00% | 100.00% |
| Payment | `POST /api/payments` | 40,778 | 1,351.44 | 6.64 ms | 0.00% | 100.00% |
| Redis | `GET /api/recommendations/customers/{id}` | 14,602 | 484.21 | 4.52 ms | 0.00% | 100.00% |
| Redis | `POST /api/recommendations/.../views` | 14,502 | 481.97 | 4.85 ms | 0.00% | 100.00% |
| Checkout | order + item + payment | 41,234 | 1,367.12 | 4.99 ms | 0.00% | 100.00% |

**Notes / observations (before):**

- Most read endpoints sit around **480–490 RPS** with p95 roughly **3–5 ms** under 50 VUs.
- `POST /api/auth/login` is the clear latency outlier (p95 **470 ms**, ~128 RPS) — bcrypt/password hashing dominates.
- First `orders-add-item` run failed **85.93%** because the script reused **one shared `orderId`** across 50 VUs → `@Version` optimistic-lock conflicts (`Row was updated or deleted by another transaction`). Not an EntityGraph/N+1 issue.
- Script fixed: each iteration creates its **own order** then adds an item (same pattern as `orders-pay`). Re-run (50 VUs / 30s): **0% failures**, **100% checks**, p95 **5.51 ms**, ~929 RPS (includes create-order + add-item HTTP calls).
- Pay / payment / checkout scripts reached **~1,350+ RPS** with low p95; treat them as scenario-specific (request mix), not directly comparable to pure CRUD GETs.
- This is the **baseline** for a future `CompletableFuture` comparison — re-run the same suite with identical VUs/duration after the change.

**Screenshots (before):**

- Grafana load-test dashboard: http://localhost:3000/d/ecommerce-load-test _(optional screenshot TBD)_  
- JVM / threads (optional): http://localhost:3000  

---

### AFTER — with CompletableFuture (independent I/O only)

Measured on 2026-07-16 after parallelizing **only** call sites where work does not depend on sibling results (`allOf` / parallel branches). Same VUs/duration (50 / 30s). Endpoints **not** listed below were left synchronous (single I/O or sequential dependency) and were not re-run.

#### Summary (optimized endpoints only)

| Metric | Value |
|--------|--------|
| Approach | Spring MVC + JDBC/Redis **blocking** + `CompletableFuture` on a dedicated `ioTaskExecutor` |
| What CF does here | Runs independent I/O **concurrently**; does **not** make JDBC/Redis non-blocking (not WebFlux/R2DBC) |
| http_req_failed | **0.00%** on re-tested endpoints |
| Checks passed | **100%** |

#### Per endpoint (re-measured)

| Module | Endpoint | Reqs | RPS | p95 | Failures | Checks |
|--------|----------|------|-----|-----|----------|--------|
| Auth | `GET /api/auth/users` | 14,550 | 483.92 | 5.87 ms | 0.00% | 100.00% |
| Redis | `GET /api/recommendations/customers/{id}` | 14,260 | 471.38 | 8.58 ms | 0.00% | 100.00% |
| Redis | `POST /api/recommendations/.../views` | 14,652 | 485.91 | **3.56 ms** | 0.00% | 100.00% |

#### Also changed in code (not a dedicated k6 row)

| Use case | Parallelism |
|----------|-------------|
| `CreateCustomerUseCase` | `findByEmail` ∥ `findByCpf` before save |
| `GetPurchaseRecommendationsUseCase` | Redis fan-out (viewers ∥ peers) + Postgres product hydration via `allOf` |
| `ProductViewGraphRedisStore.recordView` | user SET/EXPIRE ∥ product SET/EXPIRE |
| `ListUsersUseCase` | customer lookups per user via `allOf` |

**Infra note:** `spring.jpa.open-in-view=false` + Hikari `maximum-pool-size=30`. With OSIV on, CF + JPA under load deadlocked the pool (Tomcat threads held connections while waiting on `join()`).

**Notes / observations (after):**

- **`POST .../views` improved** (p95 4.85 → **3.56 ms**, ~−27%): two independent Redis branches run in parallel — textbook CF win.
- **`GET .../recommendations` got slightly worse** (p95 4.52 → 8.58 ms) with the **small seed graph**: thread-pool / `allOf` overhead dominates when there are few Redis RTTs to overlap. CF shines when fan-out is large, not when N is tiny.
- **`GET /api/auth/users` ≈ flat** (5.01 → 5.87 ms) with only 3 seed users — not enough independent lookups for a clear win.
- Login / single-CRUD endpoints were **not** wrapped in CF: sequential JDBC or bcrypt-bound; `thenApply`-style chaining would not cut wall-clock time.

**Screenshots (after):**

- Grafana load-test dashboard: http://localhost:3000/d/ecommerce-load-test  

---

### Comparison (optimized endpoints)

| Endpoint | Before p95 | After p95 | Delta |
|----------|------------|-----------|-------|
| `POST /api/recommendations/.../views` | 4.85 ms | **3.56 ms** | **−27%** |
| `GET /api/recommendations/customers/{id}` | 4.52 ms | 8.58 ms | +90% (overhead on small graph) |
| `GET /api/auth/users` | 5.01 ms | 5.87 ms | ~flat (N=3 users) |
| Error rate (those runs) | 0% | 0% | — |

**Conclusion:**

In Spring MVC + blocking JDBC/Redis, `CompletableFuture` helps when **independent** I/O can overlap (`allOf` / parallel branches) — as in `recordView`. It is **not** WebFlux: threads still block on socket wait. Purely sequential steps or tiny fan-out often stay flat or get worse due to executor overhead. Eliminating blocking wait requires a reactive stack (e.g. WebFlux + R2DBC), not CF alone.
---

## Domain rules (current)

### Order
- `PENDING` → `PAID` only  
- Cancelled orders cannot be paid  
- Paid orders cannot be cancelled  

### Product
- Stock checks before decrease  
- Product must be active and in stock to be available  

### Payment
- Only `PENDING` payments can be approved  

---

## Extra documentation

Portuguese deep-dives (optional reading):

- [`docs/ARQUITETURA.md`](docs/ARQUITETURA.md) — architecture  
- [`docs/DECISOES_ARQUITETURAIS.md`](docs/DECISOES_ARQUITETURAIS.md) — design decisions  
- [`docs/GUIA_RAPIDO.md`](docs/GUIA_RAPIDO.md) — quick API walkthrough  

---

## Roadmap

1. [x] Modular monolith + DDD packaging  
2. [x] Separate PostgreSQL schemas + Flyway  
3. [x] Redis recommendations  
4. [x] Prometheus / Grafana monitoring  
5. [x] k6 load-test suite (baseline ready)  
6. [x] Introduce `CompletableFuture` on **independent** I/O paths only  
7. [x] Fill **before / after** metrics in this README  
8. [ ] (Optional) Enrich Redis seed / fan-out to show larger CF wins on recommendations  
9. [ ] (Optional) Extract modules into microservices / messaging  

---

## License

Personal / educational project.
