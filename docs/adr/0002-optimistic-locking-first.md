# ADR-0002: Use Optimistic Locking For Initial Inventory Reservation

## Status

Accepted

## Context

Flash-sale inventory is a high-contention resource. We need to prevent overselling when many users reserve the same product concurrently.

## Decision

Use a `version` column on `inventory_items` and let JPA optimistic locking reject stale updates. Checkout retries a bounded number of times.

## Why This First

Optimistic locking is simple and performs well when conflicts are moderate. It also teaches a useful senior-backend habit: protect invariants at the data boundary instead of trusting application memory.

## Trade-Offs

It can perform poorly during extreme contention because many requests may retry and fail. Later ADRs should compare this with:

- Pessimistic locking using `SELECT FOR UPDATE`
- Redis atomic pre-reservation
- Kafka single-writer inventory commands partitioned by product ID

## Invariant

`available` must never go below zero, and successful reservations must never exceed initial stock.
