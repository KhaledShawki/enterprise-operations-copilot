# Analytics Event Consumption

## Purpose

Analytics consumes the public Operations integration-event stream and turns selected operational
facts into Analytics-owned PostgreSQL projections. The transport remains at least once: Kafka
offsets are acknowledged only after durable local processing succeeds, while immutable `eventId`
values provide durable deduplication.

Analytics never imports Operations Java types and never reads Operations tables. The platform Kafka
adapter translates the public wire contract into Analytics-owned application models.

## Transaction boundary

One Analytics consume use case owns the local processing sequence:

1. validate and translate the Kafka envelope;
2. accept the immutable event into `analytics_inbox_events`;
3. return immediately for an identical historical `eventId`;
4. apply the relevant Analytics projection through its application input port;
5. commit the inbox row and projection mutation in one PostgreSQL transaction;
6. only then allow record-level Kafka acknowledgement.

The platform configuration wraps the framework-neutral consume service in a Spring transaction.
The inbox and projection repositories are JDBC adapters participating in that transaction and
refuse mutation when no active transaction exists. A projection failure therefore rolls back a
newly inserted inbox row. No distributed Kafka/database transaction is introduced.

A crash after the database commit but before Kafka offset commit causes redelivery. The durable
inbox recognizes the same immutable event and absorbs the replay without applying the projection a
second time.

## Supported Operations contracts

The consumer recognizes all current Operations v1 contracts:

| Event | Analytics action |
| --- | --- |
| `operations.business-partner.synchronized.v1` | update the Business Partner projection |
| `operations.invoice.synchronized.v1` | update the Invoice Receivable projection |
| `operations.payment.synchronized.v1` | durably accept as currently ignored |
| `operations.receivable-allocation.applied.v1` | durably accept as currently ignored |
| `operations.receivable-allocation.reversed.v1` | durably accept as currently ignored |

Known but currently unused events are not sent to the DLT. Keeping their immutable inbox evidence
avoids treating valid Operations traffic as poison data and preserves a local history for later
Analytics projection expansion.

Unknown event types, unsupported schema versions, aggregate-contract mismatches, malformed
envelopes, invalid payload facts, and key mismatches fail closed.

## Ordering and idempotency

Kafka records use the same deterministic aggregate key as the Operations publisher:

```text
tenantId:aggregateType:aggregateId
```

The listener uses record-level acknowledgement. The Analytics application additionally enforces the
source `aggregateVersion` rules defined by the projection foundation:

- a new aggregate starts at version 1;
- the next mutation is exactly current version + 1;
- a same-version conflicting event fails;
- a version regression fails;
- a version gap is surfaced rather than skipped.

The inbox deduplicates globally by immutable `eventId`. On a duplicate id it compares the complete
immutable envelope content, including `aggregateVersion` and JSON payload. Same-id/different-content
collisions are terminal failures. A database uniqueness fence on tenant, aggregate type, aggregate id,
and aggregate version also rejects two distinct event identities claiming the same source version.

## Retry and dead-letter behavior

Retryable failures use a bounded fixed backoff. Examples include transient PostgreSQL failures,
optimistic projection races, and version gaps that may require operational recovery.

Permanent failures are not retried before dead-letter publication. Examples include malformed
contracts, event-id collisions, version conflicts or regressions, corrupted projection state, and
invalid projection facts.

After the configured retry budget is exhausted, the original key/value/partition are published to
the dedicated Analytics DLT with failure-code and retryability headers. DLT publication itself
fails closed. Replay administration is intentionally not part of this slice.

The source topic and Analytics DLT must have compatible partition counts when failed records are
preserved on their original partition.

## Persistence ownership

Analytics owns three tables introduced by Flyway V16:

- `analytics_inbox_events`;
- `analytics_business_partner_projections`;
- `analytics_invoice_receivable_projections`.

Projection rows retain the source event id, aggregate version, and occurrence timestamp. The source
event id references the durable Analytics inbox so every persisted projection has durable event
lineage.

## Deliberately deferred

This slice does not add:

- Analytics REST/query APIs;
- payment or settlement projection models;
- DLT inspection or replay administration;
- Copilot tools;
- cross-currency aggregation;
- projection rebuild administration.

Those capabilities can build on the durable consumption and projection substrate established here.
