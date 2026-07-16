# Java Ecommerce — CompletableFuture Performance Study

Modular monolith e-commerce API built with **Java 17** and **Spring Boot 3.2**, designed to measure and improve request latency by introducing **`CompletableFuture`** for concurrent work.

Repository: [noellepaes/java-ecommerce-completable-future](https://github.com/noellepaes/java-ecommerce-completable-future)

---

## What this project does

**Modular monolith** organized by DDD Bounded Contexts:

| Module | Responsibility |
|--------|----------------|
| `product` | Catalog, stock, product CRUD |
| `customer` | Customers |
| `order` | Orders, items, pay/cancel flow |
| `payment` | Payment processing |
| `auth` | Login and user listing |
| `recommendation` | Collaborative recommendations (Redis) |
| `shared` | Cross-cutting types only |

Each module: `domain` → `application` (use cases) → `infrastructure` → `presentation` (REST).

### Architecture highlights

- Single executable Spring Boot app
- PostgreSQL with separate schemas per context + Flyway
- Redis for recommendation / co-view graphs
- Modules reference each other by UUID (microservice-ready)
- Prometheus + Grafana + k6 for load testing

### Goal of this repo

Compare latency **before** and **after** `CompletableFuture` on paths with **independent** I/O (parallel `allOf` / branches) — not sequential single-call endpoints.

---

## Tech stack

- Java 17 · Spring Boot 3.2 · Spring Data JPA · Spring Data Redis  
- PostgreSQL 15 · Flyway · Redis 7  
- Micrometer · Prometheus · Grafana · Docker Compose · k6 · SpringDoc OpenAPI  

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

monitoring/
docs/
```

---

## Quick start

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

### Load tests (k6)

```powershell
cd load-tests
.\run-suite.ps1 -Vus 50 -Duration "30s"
.\run-all.ps1 -VusPerScenario 10 -Duration "30s"
```

Grafana load-test dashboard: http://localhost:3000/d/ecommerce-load-test

---

## Load test results: before & after CompletableFuture

**Conditions:** 50 VUs · 30s per scenario · Docker Compose · k6

**p95** = 95th percentile of `http_req_duration` (95% of requests finished at or below that latency).

### BEFORE — synchronous (no CompletableFuture)

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

- Login p95 ~470 ms → bcrypt.
- First `orders-add-item` hit 85% failures (shared `orderId` + `@Version`). Script now creates one order per iteration.

---

### Where CompletableFuture was applied

Four places — independent I/O only (`allOf` / parallel branches on `ioTaskExecutor`):

| Use case | Parallel work |
|----------|---------------|
| `ProductViewGraphRedisStore.recordView` | user SET/EXPIRE ∥ product SET/EXPIRE |
| `GetPurchaseRecommendationsUseCase` | Redis fan-out (viewers ∥ peers) + product hydration |
| `ListUsersUseCase` | customer lookup per user |
| `CreateCustomerUseCase` | `findByEmail` ∥ `findByCpf` |

Also: `open-in-view=false`, Hikari pool 30.

**Not** wrapped: login, single CRUD, sequential pay/add-item.

---

### AFTER — CF comparison (re-measured)

| Endpoint | Before p95 | After p95 | Delta | RPS after |
|----------|------------|-----------|-------|-----------|
| `POST /api/recommendations/.../views` | 4.85 ms | **3.56 ms** | **−27%** | 485.91 |
| `GET /api/recommendations/customers/{id}` | 4.52 ms | 8.58 ms | +90% | 471.38 |
| `GET /api/auth/users` | 5.01 ms | 5.87 ms | ~flat | 483.92 |

- **views**: gain — two Redis writes overlap.
- **recommendations GET**: worse on small seed (overhead > overlap).
- **users**: flat with 3 seed users.

---

### EntityGraph on Order (N+1) — not CF

`@EntityGraph(attributePaths = "items")` on `findById` / `findByCustomerId` / `findAll`.

After truncating ~69k stale k6 orders and seeding 30 orders × 2 items:

| Endpoint | Reqs | RPS | p95 | Failures | Checks |
|----------|------|-----|-----|----------|--------|
| `GET /api/orders/customer/{id}` | 14,552 | 482.55 | **4.87 ms** | 0.00% | 100.00% |
| `POST /api/orders/{id}/items` | 27,882 | 924.25 | 7.07 ms | 0.00% | 100.00% |
| `POST /api/orders` | 14,502 | 480.97 | 4.97 ms | 0.00% | 100.00% |

With the old 69k rows still present, list p95 was ~27s — cleanup was required before measuring.

---

## Extra docs

- [`docs/ARQUITETURA.md`](docs/ARQUITETURA.md)
- [`docs/DECISOES_ARQUITETURAIS.md`](docs/DECISOES_ARQUITETURAIS.md)
- [`docs/GUIA_RAPIDO.md`](docs/GUIA_RAPIDO.md)
