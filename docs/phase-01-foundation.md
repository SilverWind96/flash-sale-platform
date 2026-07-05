# Phase 01: Foundation And First Concurrency Invariant

## Goal

Start the flash-sale platform as a realistic backend learning project, not a CRUD-only demo. The first phase focuses on one important invariant:

```text
Inventory must never be oversold.
```

This gives us a concrete place to practice transactions, race conditions, idempotency, retries, and test design.

## What Happened

We created a new Spring Boot project in `flash-sale-platform`.

Current stack:

- Java 21 target
- Spring Boot 3.5.5
- Maven
- Spring Web
- Spring Data JPA
- Flyway
- PostgreSQL for local runtime
- H2 for fast integration tests
- Docker Compose for PostgreSQL and Redis

Current modules:

- `catalog`: product creation and product lookup
- `inventory`: stock record, available quantity, reserved quantity, optimistic lock version
- `order`: checkout flow, order persistence, idempotency key handling
- `common`: shared error handling

We also added:

- Flyway schema migration
- `README.md`
- API examples in `requests.http`
- ADRs for early design decisions
- Integration tests for idempotency and concurrent checkout

## Important Design Choices

### 1. Modular Monolith First

We did not start with microservices.

Why:

- Faster feedback loop
- Easier debugging
- Easier transaction reasoning
- Better for learning the domain invariants before adding network failure

Trade-off:

- Boundaries are enforced by package discipline, not by deployment isolation.
- It is easier to accidentally let modules know too much about each other.

Notice:

The goal is not to avoid microservices forever. The goal is to split later, after we understand the domain and failure modes.

### 2. Optimistic Locking First

Inventory has a `version` column managed by JPA `@Version`.

Why:

- Prevents stale concurrent updates from silently overwriting each other
- Good first strategy for moderate contention
- Easy to test and reason about

Trade-off:

- Under heavy flash-sale contention, many requests may conflict and retry.
- Throughput may become unstable if retry pressure is too high.

Notice:

Optimistic locking protects correctness, not user experience. Under load, users may see retry/conflict responses unless we add better queuing or reservation strategies later.

### 3. Checkout Uses An Idempotency Key

Checkout requires an `Idempotency-Key` header.

Why:

- Real clients retry after timeout
- Gateways may retry requests
- Without idempotency, one user action can create multiple orders

Trade-off:

- The first version stores the key directly on `orders`.
- It does not yet store a request hash.
- It does not yet support generic idempotency across multiple command types.
- It does not yet represent `IN_PROGRESS` idempotency states.

Notice:

This is enough for phase 01, but a senior-level version should evolve toward a dedicated idempotency table.

### 4. Retry Logic Is Outside The Transaction

`CheckoutService` owns retry behavior. `CheckoutTransaction` owns the transactional unit of work.

Why:

- A failed optimistic-lock transaction must roll back before retrying.
- Retrying inside the same failed transaction is incorrect.
- This keeps transaction boundaries visible.

Notice:

This separation is very important. A lot of production bugs come from retrying while still inside a dirty or rollback-only transaction.

## Things That Failed Or Surprised Us

### 1. Maven Could Not Write To `~/.m2` In The Sandbox

First test run failed because Maven needed to write dependency metadata under `~/.m2`.

What we learned:

- Tooling and environment problems are part of backend work.
- A build may fail before code even runs.
- Dependency cache access matters for reproducible development.

### 2. Java Runtime Mismatch

The project targets Java 21, but Maven ran tests using Java 26 from Homebrew.

What we learned:

- `java -version` and `mvn -version` can show different JDKs.
- Maven uses its own configured Java runtime.
- CI should pin the JDK explicitly.

Notice:

This project should eventually add a `.java-version`, Maven toolchain, or CI config that pins Java 21.

### 3. Mockito Failed On JDK 26

Spring Boot's test starter brought Mockito. Mockito tried to use the inline mock maker and failed to self-attach on JDK 26.

Fix:

We excluded Mockito because the current tests do not use mocks.

What we learned:

- Avoid dependencies you do not need.
- Integration tests can be cleaner without mocks.
- New JDKs can expose library compatibility issues.

### 4. The First Concurrency Test Had A Harness Bug

The test created 40 tasks but used only 16 worker threads. Each task waited at a start gate, so the first 16 occupied all workers and the remaining 24 never got scheduled.

Fix:

Use a pool large enough for all waiting tasks.

What we learned:

- Concurrency tests can have their own race conditions and deadlocks.
- A failing concurrency test does not automatically mean the business logic is wrong.
- Test harness design matters.

## What We Need To Notice Carefully

### Correctness Invariants

For this phase, always check:

```text
available >= 0
reserved >= 0
reserved <= initial_stock
successful_orders == reserved
same idempotency key returns same order
```

If any of these break, the system is not safe.

### Database Is The Source Of Truth

Do not trust Java memory to protect shared inventory.

The important protection is at the database row level:

- transaction
- version column
- constraints
- unique idempotency key

### H2 Is Not PostgreSQL

Tests currently use H2 in PostgreSQL mode.

This is fast, but not perfect.

Notice:

- Locking behavior may differ.
- Isolation behavior may differ.
- SQL support may differ.

Future work should add Testcontainers with real PostgreSQL for concurrency tests.

### Idempotency Is Not Just "Unique Key"

A unique key prevents duplicates, but full idempotency needs more:

- request hash
- response replay
- in-progress state
- expiration policy
- conflict when same key is reused with different payload

This will matter when payment and saga steps are added.

### Retry Can Make Load Worse

Retries are useful, but under high contention they can amplify traffic.

Notice:

If 1,000 requests fight for one inventory row, optimistic locking plus retry can create heavy database pressure. Later we need to compare:

- optimistic locking
- pessimistic locking
- Redis atomic pre-reservation
- Kafka single-writer inventory command processing

## Current Verification

Command:

```bash
mvn test
```

Result:

```text
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
```

Current tests:

- Reusing the same idempotency key returns the same order.
- Concurrent checkout attempts do not oversell inventory.

## What Is Not Built Yet

This phase does not include:

- Payment
- Saga
- Kafka
- Transactional outbox
- Redis reservation
- Pessimistic locking comparison
- Real PostgreSQL integration tests
- Metrics and tracing
- Load testing with k6 or Gatling

That is intentional. We first created a small reliable core.

## Next Phase

Phase 02 should compare inventory reservation strategies.

Recommended work:

1. Add a pessimistic-locking repository method.
2. Make checkout strategy selectable.
3. Run the same concurrency test against optimistic and pessimistic locking.
4. Record latency, success count, failure count, and retry count.
5. Write an ADR comparing the two approaches.

The question for phase 02:

```text
When contention gets high, which strategy fails more gracefully?
```
