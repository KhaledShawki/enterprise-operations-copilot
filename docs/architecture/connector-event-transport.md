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

The producer is configured with Kafka idempotence and `acks=all` to make broker-level retries safe
within a producer session. That does not turn the PostgreSQL-to-Kafka path into end-to-end
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

## Observability

KafkaTemplate observation and listener-container observation are enabled. Broker publication,
listener processing, failures, and consumer-client lag metrics can therefore join the platform's
Micrometer Observation pipeline without coupling Connector Management to Kafka APIs. Kafka APIs are
restricted by architecture tests to inbound/outbound Kafka adapter packages.

No Kafka health dependency is added to the application readiness group. A broker outage must not
make the HTTP/API process unavailable or prevent PostgreSQL business transactions. During a broker
outage, new integration events remain durable in the outbox and the relay retries them according to
policy.

## Topic lifecycle

The application does not create production topics. Source/DLT creation and broker policy are
operational infrastructure concerns.

The local Docker Compose environment uses a one-shot topic-initialization container because it is a
development environment. Production infrastructure should provision both topics through IaC and use
multiple brokers, an appropriate replication factor/minimum ISR, retention policy, quotas, and
TLS/SASL authentication appropriate to the deployment platform. Increasing the source topic's
partition count can remap keyed aggregates to different partitions and requires a coordinated DLT
partition-count increase, so it must be treated as an ordering-aware operational change rather than
a transparent scaling knob.

## Security boundary

The local broker uses plaintext networking only inside the private Compose network. This is a local
development convenience, not a production security model. Production bootstrap servers and Kafka
security properties remain external configuration and must use the managed broker's required TLS
and authentication mechanism.

No credentials, claim metadata, or internal retry state are placed in the event payload or Kafka
record.

## Application boundary

The Kafka listener is an inbound adapter. It calls
`ConsumeConnectorIntegrationEventUseCase`, implemented by
`ConsumeConnectorIntegrationEventService`; that service owns the call to the
`ConnectorIntegrationEventInbox` output port. The listener never invokes persistence or an output
port directly. This keeps the transport replaceable and makes retry/DLT policy an infrastructure
concern while durable consumption rules stay behind the application boundary.

This slice consumes Connector events only. Operations events remain outside the transport until a
separate event contract and ownership decision are made.
