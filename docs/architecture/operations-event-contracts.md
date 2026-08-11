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

The Operations outbox allocates `aggregateVersion` atomically with the business mutation and
enforces a unique constraint on tenant, aggregate type, aggregate id, and aggregate version. Its
relay may scale across tenants and aggregates, but it claims only the lowest unpublished version for
an aggregate. A failed head event blocks later versions for that aggregate without blocking
unrelated aggregates.

Kafka delivery remains at least once. A publication retry retains the same `eventId`, aggregate
version, and immutable content. Consumers must durably deduplicate by `eventId`, reject same-id
different-content collisions, ignore an already-applied aggregate version only when its identity is
consistent, and surface version gaps for retry or recovery. Partition offsets and timestamps are
transport evidence, not substitutes for aggregate versions.

## Transaction boundary

The transactional outbox persists the Operations mutation, import acceptance receipt or settlement
transition, and corresponding outbox rows in one PostgreSQL transaction. It does not send to Kafka
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

## Transactional outbox implementation

Operations owns two PostgreSQL tables that are independent of Connector Management:

- `operations_event_stream_versions` serializes version allocation for one tenant, aggregate type,
  and aggregate id;
- `operations_outbox_events` stores the immutable event, publication state, retry evidence, and
  fenced claim ownership.

Accepted imports and receivable-allocation transitions append their events inside the existing
business transaction. The persistence adapter requires that transaction and refuses a standalone
append. Version allocation uses one row-level conflict point per aggregate, so concurrent changes
to the same aggregate receive distinct monotonic versions while unrelated aggregates do not
contend. If payload serialization, version allocation, or outbox insertion fails, the canonical
mutation, source evidence, receipt, and event all roll back.

The relay repository claims at most the lowest unpublished version of an aggregate. `PENDING` and
due `RETRY_SCHEDULED` heads are eligible, expired `CLAIMED` heads can be reclaimed, and `FAILED`
heads continue to block later versions. `FOR UPDATE SKIP LOCKED` permits multiple workers to make
progress across independent aggregate streams. Claim owner plus publication-attempt number fences
every success, retry, and terminal-failure update.

## Kafka publication runtime

The platform supplies an Operations-owned Kafka output adapter and an inbound scheduled relay. The
relay enters through `PublishOperationsOutboxBatchUseCase`; it does not call persistence or Kafka
directly. The application service claims eligible stream heads, invokes the
`OperationsIntegrationEventPublisher` output port, and records one fenced publication outcome.

Operations uses its own `eoc.operations.integration-events` topic. The publisher validates and
deserializes the persisted payload into its typed Operations contract before producing a record.
It then emits the complete broker-neutral envelope, including `aggregateVersion`, with the
deterministic aggregate key and the immutable `occurredAt` timestamp. Claim ownership, attempts,
retry timestamps, and other outbox state never enter the wire contract.

An outbox row becomes `PUBLISHED` only after Kafka acknowledges the send. Producer idempotence and
`acks=all` protect Kafka client retries, while the PostgreSQL-to-Kafka path remains at least once.
A crash after broker acknowledgement but before the fenced database update leaves an expired claim
that can be reclaimed; republication uses the same event id, aggregate version, key, and content.

Publication waits are bounded by the shared platform `max.block.ms` budget plus the
Operations-specific acknowledgement timeout. Startup rejects a claim lease that does not exceed
the worst-case sequential budget for the configured batch:

```text
claimLease > batchSize * (maxBlockTimeout + acknowledgementWaitTimeout)
```

Retryable broker failures and acknowledgement timeouts flow through the existing durable outbox
retry policy. Invalid contracts and permanent Kafka rejections are terminal and remain a
same-aggregate ordering barrier. Unknown Kafka failures fail closed rather than retrying forever.
Multiple workers can scale across independent aggregate streams through the existing fenced
`FOR UPDATE SKIP LOCKED` claim protocol.

No Operations consumer, inbox, DLT, or replay API is added in this publication slice. Future
consumers must provide their own durable `eventId` inbox before relying on this at-least-once topic.
The existing Connector outbox, inbox, consumer, DLT, and recovery flow remain unchanged.
