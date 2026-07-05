# Load Testing

This project includes a small API-based setup script and a parallel checkout load script.

The goal is not to produce perfect benchmark numbers yet. The goal is to create pressure on the checkout path and observe whether the system keeps its core invariant:

```text
Inventory must never be oversold.
```

## Prerequisites

Start the local database:

```bash
docker compose up -d
```

Start the application:

```bash
mvn spring-boot:run
```

Application logs appear in the terminal running `mvn spring-boot:run`.

Notice:

`docker compose logs` only shows PostgreSQL and Redis logs because the Spring Boot app is not running inside Docker yet.

The scripts require:

- `curl`
- `jq`
- Bash

## Step 1: Seed Test Data

Run:

```bash
./scripts/seed-dev-data.sh
```

By default, this creates:

- one unique product
- 10,000 stock units
- `.load-test.env` containing the generated `PRODUCT_ID`

Useful options:

```bash
STOCK=50000 ./scripts/seed-dev-data.sh
SKU=LOAD-001 STOCK=1000 ./scripts/seed-dev-data.sh
BASE_URL=http://localhost:8080 STOCK=10000 ./scripts/seed-dev-data.sh
```

Notice:

The script uses the public API instead of direct SQL. That is slower, but it proves the real product and inventory flows work.

## Step 2: Run Checkout Load

Run:

```bash
./scripts/load-checkout.sh
```

By default, this sends:

- 1,000 checkout requests
- 50 concurrent workers
- quantity 1 per request
- unique idempotency key per request

Useful options:

```bash
REQUESTS=5000 CONCURRENCY=100 ./scripts/load-checkout.sh
REQUESTS=20000 CONCURRENCY=300 QUANTITY=1 ./scripts/load-checkout.sh
PRODUCT_ID=<uuid> REQUESTS=1000 CONCURRENCY=50 ./scripts/load-checkout.sh
REQUESTS=5000 CONCURRENCY=100 CONNECT_TIMEOUT=2 MAX_TIME=15 ./scripts/load-checkout.sh
```

Results are written to:

```text
target/load-test/
```

Each run creates:

- raw result TSV file
- summary text file

The summary also checks inventory before and after the run:

```text
successful_201 * quantity == reserved_after - reserved_before
available_after >= 0
```

If this check fails, treat the result as a correctness bug until proven otherwise.

## Application Logs

During a run, the application logs one line per non-actuator request:

```text
http method=POST path=/api/checkout status=201 durationMs=42 remote=0:0:0:0:0:0:0:1 idempotencyKey=load-checkout-...
```

It also logs important domain events:

```text
productCreated productId=... sku=... priceCents=2500
stockAdded productId=... quantity=10000 available=10000 reserved=0
checkoutReserved orderId=... productId=... quantity=1 amountCents=2500 idempotencyKey=...
businessError code=INSUFFICIENT_STOCK status=409 path=/api/checkout message=Not enough stock available
```

Under high load, logs are useful for learning but can slow the app and distort benchmark numbers. To disable request logs:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--flashsale.logging.requests.enabled=false
```

## Status Codes To Watch

Expected codes:

- `201`: checkout succeeded and reserved stock
- `409`: business conflict, usually insufficient stock or high optimistic-lock contention
- `400`: bad request, usually missing/invalid input
- `500`: bug or unhandled failure
- `CURL_ERROR`: client could not reach the app or connection failed

For phase 01, `409` can be acceptable under pressure. `500` is not acceptable.

## Things That Can Make The Test Lie

### Reusing The Same Idempotency Key

If every request uses the same `Idempotency-Key`, the system should return the same order. That does not test inventory pressure.

The load script creates a unique key per request:

```text
load-checkout-<run-id>-<request-number>
```

### Too Little Stock

If `REQUESTS > STOCK`, many requests should fail with `409`.

That is not necessarily a bug. It means the system refused to oversell.

### Hitting The Client Limit First

This script uses local `curl` processes. At very high concurrency, your laptop may become the bottleneck before the backend does.

Watch for:

- many `CURL_ERROR` results
- CPU spikes on the client side
- connection refused
- local port exhaustion

For serious benchmarking, add k6 or Gatling later.

### H2 Test Behavior Is Not Enough

The unit/integration tests use H2 for speed. Load testing should run against the real app and PostgreSQL through Docker Compose.

PostgreSQL locking behavior is the behavior we care about.

## What To Check After A Run

Call the inventory endpoint:

```bash
curl http://localhost:8080/api/products/${PRODUCT_ID}/inventory | jq
```

Check:

```text
available >= 0
reserved <= seeded stock
successful 201 responses * quantity == reserved increase
500 count == 0
CURL_ERROR count == 0
```

## Suggested Experiments

Start gentle:

```bash
STOCK=10000 ./scripts/seed-dev-data.sh
REQUESTS=1000 CONCURRENCY=50 ./scripts/load-checkout.sh
```

Increase contention:

```bash
REQUESTS=5000 CONCURRENCY=200 ./scripts/load-checkout.sh
```

Force insufficient stock:

```bash
STOCK=100 ./scripts/seed-dev-data.sh
REQUESTS=1000 CONCURRENCY=100 ./scripts/load-checkout.sh
```

The important question:

```text
Does the system fail safely when demand is higher than stock?
```
