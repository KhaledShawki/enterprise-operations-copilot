# Local observability

PR #74 introduces a local-only Prometheus and Grafana profile for measuring EOC runtime behavior.
The profile does not add distributed tracing; tracing remains a separate roadmap step.

## Start the platform with observability

```bash
docker compose \
  --env-file deployment/compose/.env \
  --file deployment/compose/compose.yaml \
  --file deployment/compose/compose.evidence.yaml \
  --file deployment/compose/compose.observability.yaml \
  --profile evidence \
  --profile observability \
  up -d --build
```

Prometheus is available at `http://127.0.0.1:9090` by default.
Grafana is available at `http://127.0.0.1:3000` by default and is provisioned for anonymous local Viewer access.

The platform exposes only the health and Prometheus Actuator endpoints. Prometheus scrapes:

```text
http://platform-service:8080/actuator/prometheus
```

The first provisioned dashboard is **EOC Event Pipeline**. It combines HTTP, database-pool, connector import/retry, bounded-cardinality outbox/inbox, Kafka lag, Analytics projection/freshness, DLT, and Copilot signals.

## Validate configuration

```bash
docker compose \
  --env-file deployment/compose/.env.example \
  --file deployment/compose/compose.yaml \
  --file deployment/compose/compose.observability.yaml \
  --profile observability \
  config --quiet

python3 -m json.tool \
  deployment/observability/grafana/dashboards/eoc-event-pipeline.json \
  >/dev/null
```

## Verify the scrape

After the stack is running:

```bash
curl -fsS http://127.0.0.1:${PLATFORM_HTTP_PORT:-8080}/actuator/prometheus \
  | grep process_uptime_seconds

curl -fsS 'http://127.0.0.1:9090/api/v1/query?query=up%7Bjob%3D%22eoc-platform-service%22%7D'
```

A successful Prometheus query should report the platform target with value `1`.

## Scope boundary

This observability profile is measurement infrastructure. It does not change outbox cadence, Kafka delivery semantics, retry policy, or any business behavior. Do not derive capacity claims from this initial setup.

## Event-pipeline metrics

The application-specific meters use bounded `pipeline` labels only. Micrometer canonical meter names
are exported to Prometheus with underscores and Prometheus suffixes where appropriate:

| Prometheus series | Meaning |
| --- | --- |
| `eoc_outbox_pending` | outstanding connector/Operations outbox events not yet successfully published |
| `eoc_outbox_oldest_seconds` | age of the oldest outstanding outbox event |
| `eoc_outbox_publish_total` | successfully published outbox events |
| `eoc_outbox_publish_failure_total` | publication outcomes requiring retry or terminal failure |
| `eoc_outbox_publish_duration_seconds_*` | non-empty outbox batch publication timer/histogram; empty scheduler polls are excluded |
| `eoc_inbox_received_total` | connector/Analytics transport deliveries |
| `eoc_inbox_duplicate_total` | idempotently rejected duplicate deliveries |
| `eoc_inbox_failure_total` | delivery attempts that fail processing |
| `eoc_projection_apply_total` | Analytics projections successfully applied |
| `eoc_projection_apply_duration_seconds_*` | Analytics projection application timer/histogram |
| `eoc_projection_freshness_seconds_*` | age of an Operations event when its Analytics projection is applied |
| `eoc_projection_version_gap_total` | detected projection version gaps |
| `eoc_projection_stale_event_total` | projection version regressions/stale events |
| `eoc_dlt_total` | records successfully published to a DLT |
| `eoc_dlt_replay_total` | connector DLT records successfully replayed |
| `eoc_dlt_replay_failure_total` | connector replay outcomes requiring retry or terminal failure |
| `eoc_connector_import_execution_total` | connector import executions by bounded import type and outcome (`success`, `partial`, `retry_scheduled`, `failure`, `cancelled`, `in_progress`) |
| `eoc_connector_import_retry_scheduled_total` | connector import attempts that scheduled another retry |
| `eoc_connector_import_duration_seconds_*` | connector import execution timer/histogram by bounded import type |
| `eoc_copilot_request_total` | Copilot requests by success/failure outcome |
| `eoc_copilot_duration_seconds_*` | Copilot request timer/histogram by success/failure outcome |

Histogram ranges are measurement ranges, not SLOs: connector-import, projection-freshness, and Copilot histograms cover up to 30 minutes. Copilot measures the complete multi-round request rather than a single model call.

Spring Boot also exports HikariCP connection-pool metrics. The dashboard derives database connection
utilization from `hikaricp_connections_active / hikaricp_connections_max`; no EOC-specific database
labels are added. HTTP error rate is derived from the standard `http_server_requests_seconds_*` meters.

The outbox backlog sampler runs every five seconds by default and reads only aggregate backlog state;
it never adds tenant, event, aggregate, or run identifiers to metrics. Rows in `PENDING`,
`RETRY_SCHEDULED`, or `CLAIMED` state are treated as outstanding until publication succeeds.

Kafka consumer lag comes from Micrometer's native Kafka client binding rather than an EOC-specific
per-record metric. The dashboard uses the maximum `kafka_consumer_fetch_manager_records_lag_max`
value across platform consumers, avoiding topic/partition labels in the dashboard query.

## Evidence capture

When the evidence and observability profiles are running, the deterministic baseline can include a
checksummed Prometheus snapshot in its normal evidence bundle:

```bash
./scripts/eoc-lab run baseline \
  --records 137 \
  --seed 42 \
  --capture-observability
```

The lab queries Prometheus through `GET /api/v1/query`. It first requires a drained pre-workload
snapshot that is stable across a scrape interval, then snapshots cumulative counters again after
reconciliation and records deltas in `result.json`. Expected deltas are derived
from the deterministic workload, including connector import execution/retry and projection-freshness
observation counts, so prior runs in the same process do not affect the check. The
evidence also requires connector and Operations outbox backlog gauges to be drained at the end.
Prometheus/Grafana image identities and hashes of the observability Compose, Prometheus config, and
provisioned dashboard are included in runtime provenance.

This is correctness evidence for metric wiring, not a capacity benchmark. Latency/throughput claims
remain deferred to the measured capacity work.

## Cardinality guard

A global `MeterFilter` rejects identifier-like metric tags such as `tenantId`, `event_id`, `aggregateId`,
`runId`, `traceId`, and similar variants. This protects Prometheus from accidental high-cardinality series.
Bounded operational dimensions such as `pipeline` and `outcome` remain allowed.
