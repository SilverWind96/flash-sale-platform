# ADR-0001: Start With A Modular Monolith

## Status

Accepted

## Context

The final goal is to practice backend systems that include concurrency, messaging, saga workflows, and operational trade-offs. Starting immediately with microservices would add deployment and network complexity before the domain invariants are proven.

## Decision

Start as a modular monolith with package boundaries:

- `catalog`
- `inventory`
- `order`
- `common`

Each module has explicit services and repositories. We will split modules later only when the data ownership and workflow boundaries are understood.

## Consequences

This keeps feedback fast and makes tests easier. The trade-off is that service boundaries are not enforced by the network yet, so we must stay disciplined with package ownership and avoid shared domain shortcuts.
