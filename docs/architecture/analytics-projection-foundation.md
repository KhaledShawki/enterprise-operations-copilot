# Analytics Projection Foundation

## Purpose and boundary

Analytics owns read-optimized projections derived from public integration-event contracts. It does
not import Operations domain or application classes and does not read Operations persistence. The
platform may later adapt Operations wire events into Analytics input commands, but the Analytics
module remains independent of Kafka, Spring, JPA, JSON libraries, and database APIs.

The initial slice supports the first product workflow: customer context and canonical invoice
receivables for overdue-receivable analysis.

## Initial projections

### Business Partner

`BusinessPartnerProjection` keeps the tenant-scoped partner identity, partner number, display name,
roles, and the source event cursor. It is deliberately independent from invoice state. Business
Partner and Invoice events are separate aggregate streams and can be delivered independently, so
customer names must be joined at query time rather than copied into invoice projection rows.

### Invoice Receivable

`InvoiceReceivableProjection` keeps the canonical invoice snapshot required to calculate
source-authoritative outstanding receivables:

- invoice and customer identity;
- invoice number;
- original and paid amount plus currency;
- issue and due dates;
- cancellation and canonical status;
- source event id, aggregate version, and occurrence timestamp.

Outstanding amount is derived as original amount minus the source-authoritative paid amount. A
receivable is overdue only when its due date is before the evaluation date, its canonical status is
open or partially paid, and it still has a positive outstanding amount.

Local receivable-settlement allocations are intentionally not folded into this balance. Operations
treats those allocations as local cash-application evidence and explicitly does not mutate the
source-authoritative Invoice paid amount. A later Analytics projection can model settlement and
reconciliation facts separately without double-counting them.

## Version and replay semantics

Every projection row retains the latest source `eventId`, positive `aggregateVersion`, and
`occurredAt` timestamp. Application services enforce these rules before persistence:

1. a new projection must start at aggregate version 1;
2. the next accepted event must be exactly current version + 1;
3. an exact replay of the current event identity and facts is a no-op duplicate;
4. the same version with different identity, timestamp, or facts fails closed;
5. an older version is surfaced rather than silently ignored because its historical identity cannot
   be proven from the current row alone;
6. a version gap is surfaced for retry or operational recovery;
7. reusing the current event id for a different aggregate version fails closed.

Repositories expose conditional saves fenced by the expected current aggregate version. Version zero
means the projection must not already exist. This prevents lost updates when multiple consumers or
workers race on the same projection.

## Deliberately deferred

This foundation does not add:

- Kafka consumers or topic configuration;
- a durable Analytics inbox or global event-id history;
- PostgreSQL projection tables or migrations;
- Spring configuration or REST controllers;
- payment or receivable-settlement projections;
- DLT or replay operations;
- analytical read APIs.

The next transport slice must add a durable Analytics-owned inbox before relying on the
at-least-once Operations topic. The inbox must deduplicate immutable `eventId` values, reject
same-id/different-content collisions, and coordinate inbox acceptance with projection persistence in
one local transaction.
