from __future__ import annotations

import json
import unittest
from unittest.mock import Mock

from eoc_lab.http import HttpResponse
from eoc_lab.observability import (
    PrometheusClient,
    PrometheusEvidenceError,
    PrometheusSnapshot,
    counter_deltas,
    evidence_payload,
    expected_counter_deltas,
)


class PrometheusClientTest(unittest.TestCase):
    def test_queries_prometheus_instant_vector_as_scalar(self) -> None:
        http = Mock()
        http.request.return_value = _response(7.5)
        client = PrometheusClient(http)

        value = client.query_scalar('eoc_outbox_pending{pipeline="operations"}')

        self.assertEqual(7.5, value)
        method, url = http.request.call_args.args
        self.assertEqual("GET", method)
        self.assertIn("/api/v1/query?", url)
        self.assertIn("query=", url)

    def test_rejects_non_finite_sample(self) -> None:
        http = Mock()
        http.request.return_value = _response("NaN")

        with self.assertRaisesRegex(PrometheusEvidenceError, "non-finite"):
            PrometheusClient(http).query_scalar("metric")


class PrometheusEvidenceTest(unittest.TestCase):
    def test_computes_counter_deltas_without_assuming_fresh_process(self) -> None:
        before = _snapshot(base=100.0)
        after = _snapshot(base=100.0)
        after.counters.update(
            {
                "connectorOutboxPublished": 102.0,
                "operationsOutboxPublished": 128.0,
                "connectorInboxReceived": 102.0,
                "analyticsInboxReceived": 128.0,
                "analyticsProjectionApplied": 128.0,
            }
        )

        deltas = counter_deltas(before, after)

        self.assertEqual(2.0, deltas["connectorOutboxPublished"])
        self.assertEqual(28.0, deltas["operationsOutboxPublished"])

    def test_expected_deltas_follow_customer_and_invoice_workload(self) -> None:
        expected = expected_counter_deltas(customer_count=25, invoice_count=137)

        self.assertEqual(2.0, expected["connectorOutboxPublished"])
        self.assertEqual(162.0, expected["operationsOutboxPublished"])
        self.assertEqual(162.0, expected["analyticsProjectionApplied"])
        self.assertEqual(162.0, expected["analyticsProjectionFreshnessSamples"])
        self.assertEqual(2.0, expected["connectorImportExecutions"])
        self.assertEqual(2.0, expected["connectorImportDurationSamples"])
        self.assertEqual(0.0, expected["connectorImportRetriesScheduled"])
        self.assertEqual(0.0, expected["copilotRequests"])
        self.assertEqual(0.0, expected["analyticsDeadLetters"])

    def test_evidence_requires_exact_counter_deltas_and_drained_pipeline(self) -> None:
        before = _snapshot(base=10.0)
        after = _snapshot(base=10.0)
        expected = expected_counter_deltas(customer_count=2, invoice_count=3)
        for name, delta in expected.items():
            after.counters[name] += delta
        after.counters["connectorOutboxDurationSamples"] += 2.0
        after.counters["operationsOutboxDurationSamples"] += 5.0

        payload = evidence_payload(
            client=PrometheusClient(Mock(), base_url="http://prometheus:9090"),
            before=before,
            after=after,
            customer_count=2,
            invoice_count=3,
        )

        self.assertEqual("prometheus-http-api", payload["source"])
        self.assertTrue(payload["checks"]["counterDeltasMatchWorkload"])
        self.assertTrue(payload["checks"]["pipelineDrained"])
        self.assertEqual(5.0, payload["counterDeltas"]["operationsOutboxDurationSamples"])

    def test_counter_regression_is_rejected(self) -> None:
        before = _snapshot(base=10.0)
        after = _snapshot(base=10.0)
        after.counters["analyticsInboxReceived"] = 9.0

        with self.assertRaisesRegex(PrometheusEvidenceError, "moved backwards"):
            counter_deltas(before, after)


def _snapshot(*, base: float) -> PrometheusSnapshot:
    expected = expected_counter_deltas(customer_count=0, invoice_count=0)
    names = {
        *expected.keys(),
        "connectorOutboxDurationSamples",
        "operationsOutboxDurationSamples",
    }
    counters = {name: base for name in names}
    gauges = {
        "connectorOutboxPending": 0.0,
        "operationsOutboxPending": 0.0,
        "connectorOutboxOldestSeconds": 0.0,
        "operationsOutboxOldestSeconds": 0.0,
    }
    return PrometheusSnapshot(counters=counters, gauges=gauges)


def _response(value) -> HttpResponse:
    body = json.dumps(
        {
            "status": "success",
            "data": {
                "resultType": "vector",
                "result": [{"metric": {}, "value": [1_788_000_000.0, str(value)]}],
            },
        }
    ).encode("utf-8")
    return HttpResponse(status=200, headers={"content-type": "application/json"}, body=body)


if __name__ == "__main__":
    unittest.main()
