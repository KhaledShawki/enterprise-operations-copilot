# Connector Integration Event Transport

## Purpose

Connector Management persists integration events in PostgreSQL in the same transaction as the
business state that produced them. The outbox relay then delivers those durable events through a
transport adapter. Kafka is an infrastructure choice; it is not part of the Connector Management
domain or application model.

The runtime supports two transport modes:

```text
Connector transaction
        |
        v
connector_outbox_events
        |
        v
PublishConnectorOutboxBatchService
        |
        v
ConnectorIntegrationEventPublisher
        |
        +---- local ----> ConnectorIntegrationEventInbox ----> durable inbox + projection
        |
        +---- kafka ----> Kafka source topic
                              |
                              v
                   KafkaConnectorIntegrationEventConsumer
                              |
                              v
                   ConsumeConnectorIntegrationEventUseCase
                              |
                              v
                   ConnectorIntegrationEventInbox
                              |
                              v
                    durable inbox + projection
```

`local` is the default so normal tests and lightweight development do not require a broker. The
Docker Compose platform explicitly selects `kafka` so the complete local runtime exercises the
production-style publication boundary.

## Delivery contract

The broker-neutral `ConnectorIntegrationEventEnvelope` is the only data allowed to cross the
transport boundary. It contains:

- `eventId`
- `eventType`
- `schemaVersion`
- `tenantId`
- `aggregateType`
- `aggregateId`
- `payload`
- `occurredAt`

Outbox claim ownership, publication attempt count, claim timestamps, retry timestamps, and other
relay metadata are deliberately excluded. Those values describe how this process is trying to
deliver an event; they are not facts about the event itself.

Kafka values are JSON objects containing the complete envelope. The nested `payload` remains a JSON
object rather than a JSON string. This keeps the wire format portable across languages and avoids a
second parsing layer for consumers. The inbound adapter rejects blank, oversized, malformed,
non-object, incomplete, or extended envelopes before invoking the application. UUIDs, timestamps,
field types, and the object-valued payload are validated structurally at this boundary; supported
event contracts and typed payload invariants remain application/persistence concerns.

## Topic and ordering

Connector events are published to the configured Connector integration-events topic. Event types
carry their own contract version, for example `connector.import-run.completed.v1`, so compatible
schema evolution does not require a new topic.

The Kafka record key is deterministic:

```text
tenantId:aggregateType:aggregateId
```

All events for the same tenant-owned aggregate therefore resolve to the same key and, while the topic
partition count is unchanged, Kafka maps that key consistently to one partition. This provides
partition locality; it is not a causal-order guarantee for the domain. Multiple outbox relay workers
can publish different events for the same aggregate concurrently, so consumers must not infer source
transaction order merely from Kafka offset order. If a future consumer requires causal aggregate
ordering, the event contract must first gain an explicit aggregate sequence/version and the relay must
enforce that ordering.

The record timestamp is the event's immutable `occurredAt`, not the relay publication time.

## Reliability semantics

The system intentionally provides at-least-once event delivery.

The shared platform producer is configured with Kafka idempotence and `acks=all` to make
broker-level retries safe within a producer session. Connector and Operations retain separate
topics, contracts, outboxes, and publication policies while sharing only transport-level producer
safety configuration. That does not turn either PostgreSQL-to-Kafka path into end-to-end
exactly-once delivery. A process can still receive a successful broker acknowledgement and fail
before the outbox row is marked `PUBLISHED`. After the claim lease expires, the same stable event can
be published again.

Consumers must therefore use `eventId` as the idempotency identity. The existing Connector inbox
already enforces this rule: the same event id with the same immutable content is a replay, while the
same id with different content is a collision and is rejected. JSON payload comparison uses
PostgreSQL `jsonb` equality on the duplicate slow path, so harmless object-property ordering or
whitespace changes do not create false collisions. All other immutable envelope fields must still
match exactly.

Kafka consumption is also at least once. Auto-commit is disabled and the dedicated listener
container uses record acknowledgement. A source offset advances only after one of these outcomes:

1. the application use case returns after the inbox receipt and projection commit atomically;
2. an identical `eventId` replay is absorbed by that durable inbox; or
3. bounded recovery publishes the original key and value to the DLT successfully.

If the process stops after the PostgreSQL commit but before the Kafka offset commit, Kafka redelivers
the record and inbox idempotency absorbs it. No distributed transaction is required: broker delivery
is at least once while the database effect is idempotent/effectively once.

No Kafka transaction is used for outbox publication. A Kafka transaction cannot atomically include
the PostgreSQL business transaction. The transactional outbox is the atomicity mechanism, and
adding Kafka transactions would increase complexity without removing the cross-resource failure
window.

## Acknowledgement and claim-lease budget

An outbox event is marked `PUBLISHED` only after the Kafka send future completes successfully.
Publication is therefore bounded by two producer-side waits:

1. Kafka producer `max.block.ms`, which bounds synchronous blocking while obtaining metadata or
   buffer capacity.
2. The application acknowledgement wait timeout, which bounds waiting for the asynchronous send
   result.

The relay claims a batch before publishing its events sequentially. In Kafka mode, startup therefore
fails unless the outbox claim lease is longer than the worst-case publication budget for the whole
batch:

```text
claimLease > batchSize * (maxBlockTimeout + acknowledgementWaitTimeout)
```

This prevents later events in a claimed batch from predictably aging past the lease while earlier
events wait on broker metadata or acknowledgement. The safe runtime default is deliberately small:

```text
Outbox batch size           1
Kafka max-block timeout     5s
Kafka acknowledgement wait 10s
Outbox claim lease          30s
```

A production deployment may increase the batch size only together with enough lease headroom, or
scale relay workers horizontally. Kafka's own delivery and request timeouts remain smaller than the
application acknowledgement wait as an additional bounded-failure layer.

## Failure classification

Transport failures are translated into stable `ConnectorEventPublicationException` codes so the
existing outbox policy remains the owner of retry and terminal-failure decisions.

Retryable failures include broker/transient Kafka errors, application acknowledgement timeout, and
thread interruption. Unsupported or semantically invalid event contracts are rejected before Kafka
is called. Authentication, authorization, invalid-topic, record-too-large, serialization, and other
permanent publication rejections are terminal. Unknown Kafka failures fail closed rather
than being retried indefinitely without evidence that retry can succeed.

The outbox retains its existing attempt budget and retry schedule. Kafka client retries are an
inner transport retry layer bounded by Kafka delivery timeout; outbox retry is the durable outer
retry layer.

On consumption, malformed envelopes, routing-key mismatches, unsupported contracts, invalid typed
payloads, and event-id collisions are terminal. Retryable inbox/database failures and unexpected
application failures use a finite fixed-backoff attempt budget. After terminal failure or retry
exhaustion, the dead-letter recoverer publishes to the configured DLT using the original source
partition. The DLT record preserves the source key and value, Spring Kafka failure/origin headers,
and stable Connector failure-code/retryability headers.

Startup rejects a retry-backoff plus worst-case DLT-publication budget that reaches Kafka's
`max.poll.interval`. The poll size is also bounded so one consumer does not accumulate an unbounded
record-processing horizon between polls. These safeguards avoid predictable rebalance churn while
still allowing concurrency across source partitions and horizontal instances in the same group.

DLT publication is synchronous from the recovery boundary and fails closed. A missing topic,
undersized partition set, authorization failure, or send timeout leaves the source record
unrecovered and its offset uncommitted, so it remains eligible for delivery. The source and DLT must
therefore be provisioned explicitly with the same partition count. Infinite listener retry is not
permitted. There is still no transaction spanning DLT publication and the source-offset commit, so a
crash in that narrow window can duplicate a DLT record. Operational replay must retain `eventId` and
remain idempotent rather than treating the DLT as exactly once.

## Controlled dead-letter inspection and replay

The Kafka DLT is inspected in place through a platform-admin API. Inspection uses short-lived,
manually assigned consumers that seek to explicit DLT partition/offset coordinates and never commit
offsets. It therefore cannot change either the source consumer group's recovery position or an
operator's paging cursor. Pages are bounded, reads have a finite timeout, and list responses omit the
raw key, value, and headers. Exact-record responses include that material because it is required for
diagnosis and replay and are restricted to the global `platform-admin` role.

Kafka retention remains the availability boundary for records that have not been selected for
replay. An expired DLT offset returns an explicit out-of-retained-range response; the application
does not pretend that Kafka is an indefinite audit store.

A replay request snapshots the immutable source key, value, partition, timestamp, and replayable
headers into PostgreSQL together with the requesting JWT issuer/subject, reason, and request time.
The DLT topic/partition/offset tuple is unique. Repeating a request for identical content returns the
same durable request, while reuse of the same coordinates with a different fingerprint is rejected
as a collision. This protects against topic recreation or offset reuse silently changing an audited
request.

The replay relay claims rows with `FOR UPDATE SKIP LOCKED`, a lease, worker identity, and publication
attempt fence. It republishes the original record to the original source partition and adds only
replay request/generation evidence; DLT exception/origin headers are not copied back to the source.
Transient publication failures receive bounded durable retries. Permanent failures and exhausted
attempts become terminal `FAILED` requests. Startup rejects a claim lease that cannot cover the
worst-case sequential Kafka publication budget for the configured replay batch.

Replay publication is itself at least once. A process can publish successfully and stop before the
database row becomes `REPLAYED`; after lease expiry another worker can publish the same record again.
The stable envelope `eventId` is deliberately unchanged, so the existing inbox absorbs that replay
without duplicating the projection. No PostgreSQL/Kafka distributed transaction is introduced.

Every replay increments `eoc-connector-replay-generation`. Records that fail again retain this
generation when they return to the DLT, and requests at the configured maximum generation are
rejected. Replay is never automatic and cannot form an unbounded DLT-to-source loop.

## Observability

KafkaTemplate observation and listener-container observation are enabled. Broker publication,
listener processing, failures, and consumer-client lag metrics can therefore join the platform's
Micrometer Observation pipeline without coupling either bounded-context module to Kafka APIs.
Kafka APIs are restricted by architecture tests to context-specific Kafka adapters and shared
platform producer wiring.

No Kafka health dependency is added to the application readiness group. A broker outage must not
make the HTTP/API process unavailable or prevent PostgreSQL business transactions. During a broker
outage, new integration events remain durable in the outbox and the relay retries them according to
policy.

## Topic lifecycle

The application does not create production topics. Connector source/DLT and Operations source-topic
creation and broker policy are operational infrastructure concerns.

The local Docker Compose environment uses a one-shot topic-initialization container because it is a
development environment. Production infrastructure should provision all three topics through IaC
and use multiple brokers, an appropriate replication factor/minimum ISR, retention policy, quotas,
and TLS/SASL authentication appropriate to the deployment platform. Increasing the Connector source topic's
partition count can remap keyed aggregates to different partitions and requires a coordinated DLT
partition-count increase, so it must be treated as an ordering-aware operational change rather than
a transparent scaling knob. The DLT must use delete-based retention rather than log compaction;
partition/offset is part of the audited replay identity.

## Security boundary

The local broker uses plaintext networking only inside the private Compose network. This is a local
development convenience, not a production security model. Production bootstrap servers and Kafka
security properties remain external configuration and must use the managed broker's required TLS
and authentication mechanism.

No credentials, claim metadata, or internal retry state are placed in the event payload or Kafka
record. DLT payloads can still contain tenant business data, so production Kafka ACLs and the
platform-admin API authorization must be treated as privileged operational access.

## Application boundary

The Kafka listener is an inbound adapter. It calls
`ConsumeConnectorIntegrationEventUseCase`, implemented by
`ConsumeConnectorIntegrationEventService`; that service owns the call to the
`ConnectorIntegrationEventInbox` output port. The listener never invokes persistence or an output
port directly. This keeps the transport replaceable and makes retry/DLT policy an infrastructure
concern while durable consumption rules stay behind the application boundary.

This consumer transport consumes Connector events only. Operations owns the separate event catalog
and publication contract described in
[Operations Integration Event Contracts](operations-event-contracts.md). Its scheduled relay and
Kafka publisher use Operations-owned ports and a dedicated topic; they do not reuse or invoke the
Connector outbox, inbox, consumer, DLT, or recovery flow. Operations does not consume its own events.
