# 0004 — Operations owns its integration-event contracts

- Status: Accepted
- Date: 2026-08-11

## Context

Operations owns the canonical Business Partner, Invoice, Payment, and receivable-settlement state.
Connector Management supplies source records, but its import-run events describe connector
execution rather than Operations business facts. Reusing Connector events or Connector outbox
tables would couple the bounded contexts and make future service extraction harder.

Downstream analytics, audit, and copilot projections need stable facts, durable delivery, and a way
to detect duplicate, stale, missing, or out-of-order events. Kafka partition order alone is not
sufficient because multiple relay workers may publish concurrently and partition counts can change.

## Decision

Operations owns a separate broker-neutral integration-event contract and, in a later slice, will own
its own transactional outbox. The initial event catalog is:

- `operations.business-partner.synchronized.v1`
- `operations.invoice.synchronized.v1`
- `operations.payment.synchronized.v1`
- `operations.receivable-allocation.applied.v1`
- `operations.receivable-allocation.reversed.v1`

Every event has an immutable `eventId` and a positive, monotonically increasing
`aggregateVersion`. The version is scoped by tenant, aggregate type, and aggregate id. Consumers use
`eventId` for idempotency and `aggregateVersion` for stale/gap detection; neither Kafka offsets nor
event timestamps are domain versions.

Synchronized events are immutable snapshots of accepted canonical state and include source-system
lineage. Duplicate, stale, or replayed import records do not create new events. Allocation events
describe actual state transitions; an idempotent command replay does not create another event.

The Operations module contains no Kafka, JSON, Spring, persistence, retry, or claim types. A
platform-service adapter will serialize the contract, persist it atomically with the Operations
mutation, and publish it later. Operations events use separate outbox/inbox storage and topic
ownership from Connector Management.

## Consequences

- Operations can evolve or be extracted without depending on Connector Management contracts.
- Relay implementations must preserve per-aggregate version order while still allowing parallelism
  across aggregates and tenants.
- Consumers must reject aggregate-version collisions, ignore already-applied versions only after
  verifying immutable event identity, and surface gaps instead of silently projecting partial state.
- Source modification timestamps remain lineage evidence; `occurredAt` records when Operations
  accepted the state and is not replaced by a source clock.
- Business-partner email is deliberately absent from the first contract because no identified
  consumer requires it. New payload fields require a demonstrated consumer need and compatibility
  review.

## Rejected alternatives

- **Reuse Connector events:** those events describe import-run lifecycle and are not Operations
  facts.
- **Share the Connector outbox or inbox:** this creates cross-context persistence ownership and
  blocks independent scaling and extraction.
- **Rely only on Kafka key ordering:** producer concurrency and partition changes make that evidence
  insufficient for causal projection.
- **Publish directly from application services:** a database commit and a broker send cannot be made
  atomic without a durable outbox.

## Revisit when

Revisit the catalog when a concrete consumer needs another business fact, when payload sensitivity
requirements change, or when a consumer requires a stronger cross-aggregate ordering model.
