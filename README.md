# Flash Sale Platform

This project is a learning path toward senior backend Java work. It starts as a modular monolith and will grow into concurrency experiments, transactional outbox, Kafka eventing, saga orchestration, and operational runbooks.

## Current Slice

- Java 21
- Spring Boot 3.5.5
- PostgreSQL via Docker Compose
- Flyway migrations
- Product catalog
- Inventory with optimistic locking
- Checkout with idempotency key and bounded retries
- Concurrency tests for oversell prevention

## Run Locally

Start infrastructure:

```bash
docker compose up -d
```

Run the app:

```bash
mvn spring-boot:run
```

Run tests:

```bash
mvn test
```

## Try The API

Create a product:

```bash
curl -X POST http://localhost:8080/api/products \
  -H 'Content-Type: application/json' \
  -d '{"sku":"TICKET-001","name":"Concert Ticket","priceCents":2500}'
```

Add stock:

```bash
curl -X POST http://localhost:8080/api/products/{productId}/inventory/stock \
  -H 'Content-Type: application/json' \
  -d '{"quantity":500}'
```

Checkout:

```bash
curl -X POST http://localhost:8080/api/checkout \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: demo-key-001' \
  -d '{"productId":"{productId}","quantity":1}'
```

## Learning Roadmap

1. Protect inventory with optimistic and pessimistic locking.
2. Add load tests that prove which approach breaks first.
3. Add transactional outbox and Kafka publishing.
4. Add idempotent consumers and duplicate-event tests.
5. Build saga orchestration for order, inventory, payment, and shipment.
6. Split selected modules into services after the modular monolith is correct.
7. Add metrics, tracing, dashboards, and failure runbooks.

## Phase Notes

- [Phase 01: Foundation And First Concurrency Invariant](docs/phase-01-foundation.md)
- [Load Testing](docs/load-testing.md)
