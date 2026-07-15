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

### Test conditions (fill in)

| Field | Value |
|-------|--------|
| Date | _TBD_ |
| Branch / commit (before) | _TBD_ |
| Branch / commit (after) | _TBD_ |
| VUs | _e.g. 50_ |
| Duration | _e.g. 30s_ |
| Script | _e.g. `run-suite.ps1` / `run-all.ps1` / scenario name_ |
| Environment | _e.g. Docker Compose on local machine_ |

---

### BEFORE — synchronous baseline (no CompletableFuture)

_Paste results after the first measurement run._

#### Summary

| Metric | Value |
|--------|--------|
| Total requests | |
| Requests/s (avg) | |
| http_req_duration p95 | |
| http_req_failed | |
| Checks passed | |

#### Per endpoint (optional)

| Module | Endpoint | Reqs | RPS | p95 | Failures | Checks |
|--------|----------|------|-----|-----|----------|--------|
| Auth | `POST /api/auth/login` | | | | | |
| Auth | `GET /api/auth/users` | | | | | |
| Product | `GET /api/products` | | | | | |
| Product | `GET /api/products/{id}` | | | | | |
| Customer | `GET /api/customers` | | | | | |
| Customer | `GET /api/customers/{id}` | | | | | |
| Order | `GET /api/orders/customer/{id}` | | | | | |
| Order | `POST /api/orders` | | | | | |
| Order | `POST /api/orders/{id}/items` | | | | | |
| Order | `POST /api/orders/{id}/pay` | | | | | |
| Payment | `POST /api/payments` | | | | | |
| Redis | `GET /api/recommendations/customers/{id}` | | | | | |
| Redis | `POST /api/recommendations/.../views` | | | | | |
| Checkout | order + item + payment | | | | | |

**Notes / observations (before):**

-  
-  

**Screenshots (before):**

- Grafana load-test dashboard: _link or `docs/screenshots/before-load-test.png`_  
- JVM / threads (optional): _link or path_  

---

### AFTER — with CompletableFuture

_Paste results after implementing parallel orchestration._

#### Summary

| Metric | Value |
|--------|--------|
| Total requests | |
| Requests/s (avg) | |
| http_req_duration p95 | |
| http_req_failed | |
| Checks passed | |

#### Per endpoint (optional)

| Module | Endpoint | Reqs | RPS | p95 | Failures | Checks |
|--------|----------|------|-----|-----|----------|--------|
| Auth | `POST /api/auth/login` | | | | | |
| Auth | `GET /api/auth/users` | | | | | |
| Product | `GET /api/products` | | | | | |
| Product | `GET /api/products/{id}` | | | | | |
| Customer | `GET /api/customers` | | | | | |
| Customer | `GET /api/customers/{id}` | | | | | |
| Order | `GET /api/orders/customer/{id}` | | | | | |
| Order | `POST /api/orders` | | | | | |
| Order | `POST /api/orders/{id}/items` | | | | | |
| Order | `POST /api/orders/{id}/pay` | | | | | |
| Payment | `POST /api/payments` | | | | | |
| Redis | `GET /api/recommendations/customers/{id}` | | | | | |
| Redis | `POST /api/recommendations/.../views` | | | | | |
| Checkout | order + item + payment | | | | | |

**Notes / observations (after):**

- Where `CompletableFuture` was applied: _e.g. checkout orchestration / recommendations_  
-  
-  

**Screenshots (after):**

- Grafana load-test dashboard: _link or `docs/screenshots/after-load-test.png`_  
- JVM / threads (optional): _link or path_  

---

### Comparison (fill after both runs)

| Metric | Before | After | Delta |
|--------|--------|-------|-------|
| RPS | | | |
| p95 latency | | | |
| Error rate | | | |
| CPU / threads (notes) | | | |

**Conclusion:**

_Write a short conclusion: which endpoints improved, which stayed the same, and why._

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
6. [ ] Introduce `CompletableFuture` on critical paths  
7. [ ] Fill **before / after** metrics in this README  
8. [ ] (Optional) Extract modules into microservices / messaging  

---

## License

Personal / educational project.
