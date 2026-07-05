# ADR-0003: Require An Idempotency Key For Checkout

## Status

Accepted

## Context

Clients and gateways retry requests after timeouts. Without idempotency, a retry can create duplicate orders or reserve stock twice.

## Decision

Checkout requires an `Idempotency-Key` header. The first version stores the key as a unique column on `orders`.

## Consequences

Repeated calls with the same key return the same order. This is enough for the first slice, but it has limits:

- It does not yet store a request hash.
- It does not distinguish an in-progress request from a completed request.
- It does not yet support a reusable idempotency table across multiple commands.

Those limitations are useful future work, not hidden mistakes.
