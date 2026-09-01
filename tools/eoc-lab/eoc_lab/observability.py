from __future__ import annotations

from dataclasses import dataclass
import math
import time
from typing import Callable
from urllib.parse import urlencode

from eoc_lab.http import HttpClient


DEFAULT_PROMETHEUS_BASE_URL = "http://127.0.0.1:9090"
POLL_INTERVAL_SECONDS = 0.5
CAPTURE_TIMEOUT_SECONDS = 20.0
STABILITY_INTERVAL_SECONDS = 5.5

_COUNTER_QUERIES = {
    "connectorOutboxPublished": 'sum(eoc_outbox_publish_total{pipeline="connector"})',
    "operationsOutboxPublished": 'sum(eoc_outbox_publish_total{pipeline="operations"})',
    "connectorOutboxPublishFailures": 'sum(eoc_outbox_publish_failure_total{pipeline="connector"})',
    "operationsOutboxPublishFailures": 'sum(eoc_outbox_publish_failure_total{pipeline="operations"})',
    "connectorInboxReceived": 'sum(eoc_inbox_received_total{pipeline="connector"})',
    "analyticsInboxReceived": 'sum(eoc_inbox_received_total{pipeline="analytics"})',
    "connectorInboxDuplicates": 'sum(eoc_inbox_duplicate_total{pipeline="connector"})',
    "analyticsInboxDuplicates": 'sum(eoc_inbox_duplicate_total{pipeline="analytics"})',
    "connectorInboxFailures": 'sum(eoc_inbox_failure_total{pipeline="connector"})',
    "analyticsInboxFailures": 'sum(eoc_inbox_failure_total{pipeline="analytics"})',
    "analyticsProjectionApplied": 'sum(eoc_projection_apply_total{pipeline="analytics"})',
    "analyticsProjectionVersionGaps": 'sum(eoc_projection_version_gap_total{pipeline="analytics"})',
    "analyticsProjectionStaleEvents": 'sum(eoc_projection_stale_event_total{pipeline="analytics"})',
    "connectorDeadLetters": 'sum(eoc_dlt_total{pipeline="connector"})',
    "analyticsDeadLetters": 'sum(eoc_dlt_total{pipeline="analytics"})',
    "connectorDeadLetterReplayed": 'sum(eoc_dlt_replay_total{pipeline="connector"})',
    "connectorDeadLetterReplayFailures": 'sum(eoc_dlt_replay_failure_total{pipeline="connector"})',
    "connectorOutboxDurationSamples": 'sum(eoc_outbox_publish_duration_seconds_count{pipeline="connector"})',
    "operationsOutboxDurationSamples": 'sum(eoc_outbox_publish_duration_seconds_count{pipeline="operations"})',
    "connectorImportExecutions": "sum(eoc_connector_import_execution_total)",
    "connectorImportRetriesScheduled": "sum(eoc_connector_import_retry_scheduled_total)",
    "connectorImportDurationSamples": "sum(eoc_connector_import_duration_seconds_count)",
    "analyticsProjectionFreshnessSamples": 'sum(eoc_projection_freshness_seconds_count{pipeline="analytics"})',
    "copilotRequests": "sum(eoc_copilot_request_total)",
}

_GAUGE_QUERIES = {
    "connectorOutboxPending": 'max(eoc_outbox_pending{pipeline="connector"})',
    "operationsOutboxPending": 'max(eoc_outbox_pending{pipeline="operations"})',
    "connectorOutboxOldestSeconds": 'max(eoc_outbox_oldest_seconds{pipeline="connector"})',
    "operationsOutboxOldestSeconds": 'max(eoc_outbox_oldest_seconds{pipeline="operations"})',
}

_ZERO_DELTA_COUNTERS = (
    "connectorOutboxPublishFailures",
    "operationsOutboxPublishFailures",
    "connectorInboxDuplicates",
    "analyticsInboxDuplicates",
    "connectorInboxFailures",
    "analyticsInboxFailures",
    "analyticsProjectionVersionGaps",
    "analyticsProjectionStaleEvents",
    "connectorDeadLetters",
    "analyticsDeadLetters",
    "connectorDeadLetterReplayed",
    "connectorDeadLetterReplayFailures",
    "connectorImportRetriesScheduled",
    "copilotRequests",
)


class PrometheusEvidenceError(RuntimeError):
    pass


@dataclass(frozen=True)
class PrometheusSnapshot:
    counters: dict[str, float]
    gauges: dict[str, float]

    def as_dict(self) -> dict[str, dict[str, float]]:
        return {"counters": self.counters, "gauges": self.gauges}


class PrometheusClient:
    def __init__(
        self,
        http: HttpClient,
        *,
        base_url: str = DEFAULT_PROMETHEUS_BASE_URL,
    ) -> None:
        self._http = http
        self._base_url = base_url.rstrip("/")

    @property
    def base_url(self) -> str:
        return self._base_url

    def query_scalar(self, query: str) -> float:
        response = self._http.request(
            "GET", f"{self._base_url}/api/v1/query?{urlencode({'query': query})}"
        )
        if response.status != 200:
            raise PrometheusEvidenceError(
                f"Prometheus query failed with HTTP {response.status}: {query}"
            )
        payload = response.json()
        if payload.get("status") != "success":
            raise PrometheusEvidenceError(f"Prometheus query was not successful: {query}")
        data = payload.get("data")
        if not isinstance(data, dict) or data.get("resultType") != "vector":
            raise PrometheusEvidenceError(f"Prometheus query did not return an instant vector: {query}")
        result = data.get("result")
        if not isinstance(result, list) or len(result) != 1:
            raise PrometheusEvidenceError(
                f"Prometheus query must resolve to exactly one series, got {len(result) if isinstance(result, list) else 'invalid'}: {query}"
            )
        value = result[0].get("value")
        if not isinstance(value, list) or len(value) != 2:
            raise PrometheusEvidenceError(f"Prometheus query returned an invalid sample: {query}")
        try:
            scalar = float(value[1])
        except (TypeError, ValueError) as exception:
            raise PrometheusEvidenceError(
                f"Prometheus query returned a non-numeric sample: {query}"
            ) from exception
        if not math.isfinite(scalar):
            raise PrometheusEvidenceError(f"Prometheus query returned a non-finite sample: {query}")
        return scalar


def capture_snapshot(client: PrometheusClient) -> PrometheusSnapshot:
    counters = {name: client.query_scalar(query) for name, query in _COUNTER_QUERIES.items()}
    gauges = {name: client.query_scalar(query) for name, query in _GAUGE_QUERIES.items()}
    return PrometheusSnapshot(counters=counters, gauges=gauges)


def wait_for_snapshot(
    client: PrometheusClient,
    predicate: Callable[[PrometheusSnapshot], bool],
    *,
    timeout_seconds: float = CAPTURE_TIMEOUT_SECONDS,
    poll_interval_seconds: float = POLL_INTERVAL_SECONDS,
) -> PrometheusSnapshot:
    deadline = time.monotonic() + timeout_seconds
    last_error: Exception | None = None
    while True:
        try:
            snapshot = capture_snapshot(client)
            if predicate(snapshot):
                return snapshot
            last_error = None
        except (OSError, ValueError, PrometheusEvidenceError) as exception:
            last_error = exception

        if time.monotonic() >= deadline:
            if last_error is not None:
                raise PrometheusEvidenceError(
                    f"Prometheus metrics did not become available: {last_error}"
                ) from last_error
            raise PrometheusEvidenceError("Prometheus metrics did not reach the expected state")
        time.sleep(poll_interval_seconds)


def wait_for_stable_snapshot(
    client: PrometheusClient,
    predicate: Callable[[PrometheusSnapshot], bool],
    *,
    timeout_seconds: float = CAPTURE_TIMEOUT_SECONDS,
    stability_interval_seconds: float = STABILITY_INTERVAL_SECONDS,
) -> PrometheusSnapshot:
    deadline = time.monotonic() + timeout_seconds
    previous = wait_for_snapshot(client, predicate, timeout_seconds=timeout_seconds)
    while True:
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            raise PrometheusEvidenceError("Prometheus metrics did not become stable")
        time.sleep(min(stability_interval_seconds, remaining))
        current = capture_snapshot(client)
        if predicate(current) and current == previous:
            return current
        previous = current


def counter_deltas(before: PrometheusSnapshot, after: PrometheusSnapshot) -> dict[str, float]:
    if before.counters.keys() != after.counters.keys():
        raise PrometheusEvidenceError("Prometheus counter snapshots do not contain the same series")
    deltas: dict[str, float] = {}
    for name, before_value in before.counters.items():
        delta = after.counters[name] - before_value
        if delta < 0:
            raise PrometheusEvidenceError(f"Prometheus counter moved backwards: {name}")
        deltas[name] = delta
    return deltas


def expected_counter_deltas(*, customer_count: int, invoice_count: int) -> dict[str, float]:
    operations_events = customer_count + invoice_count
    expected = {
        "connectorOutboxPublished": 2.0,
        "operationsOutboxPublished": float(operations_events),
        "connectorInboxReceived": 2.0,
        "analyticsInboxReceived": float(operations_events),
        "analyticsProjectionApplied": float(operations_events),
        "connectorImportExecutions": 2.0,
        "connectorImportDurationSamples": 2.0,
        "analyticsProjectionFreshnessSamples": float(operations_events),
    }
    expected.update({name: 0.0 for name in _ZERO_DELTA_COUNTERS})
    return expected


def evidence_payload(
    *,
    client: PrometheusClient,
    before: PrometheusSnapshot,
    after: PrometheusSnapshot,
    customer_count: int,
    invoice_count: int,
) -> dict[str, object]:
    deltas = counter_deltas(before, after)
    expected = expected_counter_deltas(customer_count=customer_count, invoice_count=invoice_count)
    counter_checks = {name: deltas[name] == value for name, value in expected.items()}
    gauges_drained = all(
        after.gauges[name] == 0.0
        for name in (
            "connectorOutboxPending",
            "operationsOutboxPending",
            "connectorOutboxOldestSeconds",
            "operationsOutboxOldestSeconds",
        )
    )
    return {
        "source": "prometheus-http-api",
        "baseUrl": client.base_url,
        "queries": {"counters": _COUNTER_QUERIES, "gauges": _GAUGE_QUERIES},
        "before": before.as_dict(),
        "after": after.as_dict(),
        "counterDeltas": deltas,
        "expectedCounterDeltas": expected,
        "checks": {
            "counterDeltasMatchWorkload": all(counter_checks.values()),
            "counterDeltaChecks": counter_checks,
            "pipelineDrained": gauges_drained,
        },
    }
