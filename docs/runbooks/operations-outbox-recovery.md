# Operations Outbox Recovery

Use this runbook when an Operations integration event reaches terminal `FAILED` state and blocks
later versions of the same aggregate.

Recovery is intentionally manual. Correct the underlying failure before requesting recovery. A
recovery does not edit the event, skip it, or create a replacement event. It creates audit evidence
and gives the same failed event a fresh bounded publication generation.

## Preconditions

- authenticate as a user with the global `platform-admin` realm role;
- confirm the platform PostgreSQL database is healthy;
- identify and correct the cause represented by `lastFailureCode` before requeueing the event;
- do not use Connector DLT replay procedures for Operations outbox failures.

The examples below assume a bearer token is available as `TOKEN` and the local platform is listening
on `http://localhost:8080`.

## Inspect failed events

```bash
curl --fail-with-body \
  --header "Authorization: Bearer ${TOKEN}" \
  'http://localhost:8080/api/v1/admin/operations-outbox/events?status=FAILED&limit=20'
```

The response contains operational metadata, attempt counters, recovery generation, aggregate
identity, timestamps, and failure code. It intentionally does not contain the stored event payload.

For the next page, supply both cursor values returned by the previous response:

```bash
curl --fail-with-body \
  --header "Authorization: Bearer ${TOKEN}" \
  --get \
  --data-urlencode 'status=FAILED' \
  --data-urlencode 'limit=20' \
  --data-urlencode 'cursorCreatedAt=<nextCursorCreatedAt>' \
  --data-urlencode 'cursorEventId=<nextCursorEventId>' \
  'http://localhost:8080/api/v1/admin/operations-outbox/events'
```

Optional filters include `tenantId`, `aggregateType`, and `aggregateId`. An `aggregateId` must be
accompanied by `aggregateType`.

## Inspect one event

```bash
EVENT_ID='<event-id>'

curl --fail-with-body \
  --header "Authorization: Bearer ${TOKEN}" \
  "http://localhost:8080/api/v1/admin/operations-outbox/events/${EVENT_ID}"
```

Before recovery, verify that `status` is `FAILED`. Record the aggregate identity/version,
`publicationAttemptCount`, `recoveryGeneration`, `generationAttemptCount`, and `lastFailureCode` in
the incident or change record.

## Request recovery

After the root cause is corrected:

```bash
curl --fail-with-body \
  --request POST \
  --header "Authorization: Bearer ${TOKEN}" \
  --header 'Content-Type: application/json' \
  --data '{"reason":"Kafka broker configuration corrected and publication path verified"}' \
  "http://localhost:8080/api/v1/admin/operations-outbox/events/${EVENT_ID}/recoveries"
```

A successful request returns `201 Created`. The event moves from `FAILED` to `RETRY_SCHEDULED`, its
`recoveryGeneration` increments, and `generationAttemptCount` resets to zero. The lifetime
`publicationAttemptCount` is not reset.

A `409 Conflict` means the event is no longer in the recoverable state or the protected ordering
precondition is not satisfied. Re-inspect the event instead of retrying the request blindly.

## Verify recovery

Inspect the event again:

```bash
curl --fail-with-body \
  --header "Authorization: Bearer ${TOKEN}" \
  "http://localhost:8080/api/v1/admin/operations-outbox/events/${EVENT_ID}"
```

The normal scheduled Operations outbox relay performs publication. Do not manually change database
state. After broker acknowledgement the event becomes `PUBLISHED`; only then can a later version of
the same aggregate become publishable.

Inspect recovery audit history with:

```bash
curl --fail-with-body \
  --header "Authorization: Bearer ${TOKEN}" \
  "http://localhost:8080/api/v1/admin/operations-outbox/events/${EVENT_ID}/recoveries?limit=20"
```

Recovery history includes the operator identity, reason, previous failure evidence, request and
completion timestamps, and the recovery generation. For additional pages, pass the returned
`nextBeforeGeneration` as `beforeGeneration`.

## Failure handling

If the recovered event reaches `FAILED` again, do not repeatedly recover it without investigation.
The new failure belongs to the current recovery generation and indicates that the cause was not
fully corrected or that a different failure occurred. Inspect the new failure code and platform/Kafka
telemetry, correct the cause, and create another explicit recovery only when justified.

Never:

- update `operations_outbox_events` manually;
- change an event ID, aggregate version, occurrence timestamp, or payload;
- mark a failed event `PUBLISHED` to unblock a stream;
- delete a failed aggregate head;
- replay an Operations outbox event through the Connector DLT recovery API.
