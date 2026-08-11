# 0006 — Add audited Operations outbox recovery

- Status: Accepted
- Date: 2026-08-11

## Context

ADR 0005 intentionally leaves a terminally failed Operations outbox event as an ordering barrier.
That protects per-aggregate ordering, but production operation also requires a safe way to inspect a
failed stream head and retry it after the underlying cause has been corrected.

The existing `publish_attempt_count` is also part of claim fencing. Resetting it during recovery
would weaken stale-worker protection and destroy useful forensic history. At the same time, applying
the original retry limit to that lifetime count would give an event that already exhausted its
budget only one new attempt after manual recovery.

## Decision

Operations keeps one immutable outbox event and adds explicit recovery generations.

- `publish_attempt_count` remains a monotonically increasing lifetime claim number and continues to
  fence publication outcomes.
- `recovery_generation` starts at zero and increments only after an explicit successful operator
  recovery.
- `generation_attempt_count` is incremented on every claim and is reset to zero only when a failed
  event is recovered.
- the bounded publication retry policy is evaluated against `generation_attempt_count`, so each
  manually approved recovery generation receives a fresh bounded retry budget without resetting the
  lifetime fence.

Recovery is a synchronous PostgreSQL transaction. It locks the target row, requires the event to be
`FAILED`, verifies that no earlier unpublished aggregate version exists, appends an immutable record
to `operations_outbox_recoveries`, and moves the same event to `RETRY_SCHEDULED`. The event ID, event
type, schema version, tenant, aggregate identity/version, payload, and occurrence timestamp are not
changed.

The administration API enters through Operations-owned input ports and is restricted to the global
`platform-admin` role. List/detail responses expose operational metadata and failure evidence but do
not expose the stored event payload. Recovery records retain operator issuer/subject, reason,
previous attempt evidence, failure code, request time, and completion time.

## Consequences

- a recovered aggregate head remains the only publishable version for that aggregate until it is
  acknowledged and marked `PUBLISHED`;
- unrelated aggregates remain independently claimable;
- stale workers remain fenced by the lifetime publication-attempt number;
- manual recovery cannot silently mutate or replace integration events;
- repeated manual recovery remains explicit and auditable rather than becoming an automatic retry
  loop;
- downstream consumers still require durable `eventId` deduplication because publication remains at
  least once.

## Rejected alternatives

- **Reset `publish_attempt_count`:** weakens fencing evidence and loses lifetime publication history.
- **Create a replacement outbox event:** changes event identity and risks aggregate-version gaps or
  duplicate business facts.
- **Mark the failed event as published:** silently skips a committed business fact.
- **Reuse Connector DLT replay:** couples different bounded contexts and solves a different failure
  boundary; Operations recovery acts on its PostgreSQL outbox, not a consumed Kafka dead letter.
- **Automatically recover failed rows:** can create an unbounded failure loop and removes deliberate
  operator approval.

## Revisit when

Revisit the generation model only if measured operational evidence shows that a different retry
budget or explicit approval workflow is required. Do not relax immutable event identity or
same-aggregate ordering without a separate architecture decision.
