# 0005 — Use an Operations-owned transactional outbox

- Status: Accepted
- Date: 2026-08-11

## Context

Operations mutations and their integration events must either commit together or both roll back.
Sending to Kafka inside a database transaction cannot provide that guarantee and would couple
business-write availability to broker availability. Operations also needs a monotonic version per
tenant and aggregate so consumers can distinguish replay, stale state, and gaps independently of
Kafka offsets.

Multiple relay workers must be able to publish unrelated aggregates concurrently. They must not
publish a later version while an earlier version of the same aggregate is pending, retrying,
claimed, or terminally failed. A worker that loses an expired claim must not overwrite the current
owner's outcome.

## Decision

Operations owns two PostgreSQL tables, separate from the Connector outbox and inbox:

- `operations_event_stream_versions` atomically allocates versions within the composite stream
  identity `(tenant_id, aggregate_type, aggregate_id)`;
- `operations_outbox_events` stores the immutable event and its publication state.

The existing import and receivable-settlement transaction boundaries append the event after the
canonical mutation is persisted but before the transaction commits. The append adapter requires an
existing transaction rather than silently creating an independent one. Event identity is generated
once during the append. A uniqueness constraint protects each aggregate version.

Claiming uses `FOR UPDATE SKIP LOCKED` across eligible stream heads. A non-published predecessor
blocks only later versions of the same aggregate. Every publication outcome is fenced by event id,
claim owner, and publication-attempt number. Expired claims can be reclaimed; retry is bounded; a
terminal failure remains an ordering barrier until explicit operational recovery is introduced.

This decision does not introduce a Kafka publisher or scheduled relay. Events remain `PENDING`
until the transport slice is configured, so deploying this schema cannot discard events through a
placeholder publisher.

## Consequences

- A business mutation survives Kafka downtime as a durable unpublished event.
- A rollback caused by serialization, version allocation, or event insertion removes the business
  mutation, acceptance evidence, and outbox work together.
- A crash after broker acknowledgement but before the published marker can send the same immutable
  `eventId` again; consumers must be idempotent.
- Same-aggregate writes contend on one small stream-version row by design; unrelated aggregates and
  tenants allocate versions independently.
- A terminal head failure stops that aggregate rather than creating a silent version gap, while
  unrelated aggregates continue.
- Operators will need explicit visibility and recovery for terminal Operations events before the
  transport is considered production complete.

## Rejected alternatives

- **Publish directly from mutation services:** creates an unsafe database/broker dual write and
  makes broker availability part of the write path.
- **Reuse entity persistence versions:** those versions describe storage concurrency and do not
  consistently represent accepted integration-event transitions.
- **Reuse Connector outbox tables:** violates bounded-context ownership and couples independent
  event catalogs and recovery policies.
- **Allow any eligible row to publish:** increases throughput at the cost of same-aggregate gaps and
  out-of-order projection.
- **Hold one global sequence lock:** provides unnecessary cross-aggregate ordering and creates a
  system-wide throughput bottleneck.

## Revisit when

Revisit the claim strategy only if measured hot-aggregate contention requires a different stream
model, or if a concrete consumer requires atomic ordering across multiple aggregate identities.
