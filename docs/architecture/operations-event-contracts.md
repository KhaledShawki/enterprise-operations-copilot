# Operations Integration Event Contracts

## Purpose and ownership

Operations publishes facts about canonical operational state. Connector Management remains the
owner of source acquisition and import-run lifecycle events; it does not publish Operations facts or
relay the Operations outbox. The platform service may wire both bounded contexts, but infrastructure
wiring does not transfer contract or persistence ownership.

The first expected consumers are independently owned analytics, audit, and copilot projections.
Operations does not consume its own integration events to complete the transaction that produced
them.

## Initial catalog

| Event type | Aggregate | Meaning |
| --- | --- | --- |
| `operations.business-partner.synchronized.v1` | `BUSINESS_PARTNER` | A source record created or changed the canonical partner snapshot. |
| `operations.invoice.synchronized.v1` | `INVOICE` | A source record created or changed the canonical invoice snapshot. |
| `operations.payment.synchronized.v1` | `PAYMENT` | A source record created or changed the canonical payment snapshot. |
| `operations.receivable-allocation.applied.v1` | `RECEIVABLE_SETTLEMENT` | A new allocation became active. |
| `operations.receivable-allocation.reversed.v1` | `RECEIVABLE_SETTLEMENT` | An active allocation was reversed. |

`synchronized` means that Operations accepted canonical state. It does not mean that every fetched
record creates an event. Duplicate and stale source records, repeated accepted pages, and failed
transactions create no event. Allocation command replays likewise create no event unless they cause
a new state transition.

## Envelope

The broker-neutral envelope contains:

- `eventId`: durable idempotency identity
- `eventType` and `schemaVersion`: versioned contract identity
- `tenantId`
- `aggregateType` and `aggregateId`: routing and ownership identity
- `aggregateVersion`: positive monotonic version within that aggregate identity
- `payload`: non-blank serialized object supplied by an infrastructure adapter
- `occurredAt`: the instant Operations accepted the mutation

Kafka claims, attempts, leases, offsets, partitions, retry state, DLT evidence, and serializer types
are not part of the contract.

## Source lineage and data minimization

Synchronized payloads include the source system, source identity kind/value, opaque source version,
and optional source modification timestamp. The source version is equality evidence; it is not
assumed to be lexically or numerically ordered.

Payloads contain enough canonical state for a projection without reading the Operations database.
Derived status fields are validated against their monetary/state facts before an event can be
created. Business-partner email is excluded because the initial consumers do not require it and
event streams broaden the data-access surface.

## Ordering, partitioning, and idempotency

The transport key is:

```text
tenantId:aggregateType:aggregateId
```

The future outbox must allocate `aggregateVersion` atomically with the business mutation and enforce
a unique constraint on tenant, aggregate type, aggregate id, and aggregate version. Its relay may
scale across tenants and aggregates, but it must publish only the lowest unpublished version for an
aggregate. A failed head event blocks later versions for that aggregate without blocking unrelated
aggregates.

Kafka delivery remains at least once. A publication retry retains the same `eventId`, aggregate
version, and immutable content. Consumers must durably deduplicate by `eventId`, reject same-id
different-content collisions, ignore an already-applied aggregate version only when its identity is
consistent, and surface version gaps for retry or recovery. Partition offsets and timestamps are
transport evidence, not substitutes for aggregate versions.

## Transaction boundary

The later outbox slice must persist the Operations mutation, import acceptance receipt or settlement
transition, and corresponding outbox rows in one PostgreSQL transaction. It must not send to Kafka
inside that transaction. Rollback removes both state and events; commit makes both durable.

The following failure rules are mandatory:

- a transaction rollback produces no event;
- a page or command replay that changes no state produces no event;
- a crash after commit leaves an unpublished durable event;
- a crash after Kafka acknowledgement but before marking publication complete can republish the
  same event;
- one aggregate's terminal publication failure must not silently allow its later versions to pass;
- another aggregate must continue to make progress.

## Evolution

Event names include their major schema generation (`.v1`) and the numeric `schemaVersion` must
match. Additive compatible changes remain within a version only when all supported consumers accept
them. Renames, removals, semantic changes, or changed invariants require a new event type/version and
an explicit migration window. Topics are not versioned solely for schema changes.

## Current implementation boundary

This slice defines and tests the Operations-owned contracts only. It does not add an Operations
outbox table, relay, Kafka adapter, topic, inbox, or consumer. Those components must depend on these
contracts and must not alter the existing Connector outbox/inbox flow.
