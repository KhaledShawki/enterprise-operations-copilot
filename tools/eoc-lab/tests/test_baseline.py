from __future__ import annotations

from datetime import datetime, timezone
from decimal import Decimal
from pathlib import Path
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch
from uuid import UUID

import eoc_lab.baseline as baseline
from eoc_lab.baseline_dataset import expected_dataset
from eoc_lab.doctor import DoctorReport
from eoc_lab.evidence import RepositoryState
from eoc_lab.observability import PrometheusSnapshot, expected_counter_deltas
from eoc_lab.reconciliation import expected_summary


TENANT_ID = "00000000-0000-0000-0000-000000000101"
CONNECTOR_ID = "00000000-0000-0000-0000-000000000102"
PLATFORM_USER_ID = "00000000-0000-0000-0000-000000000103"


class CapturingEvidenceStore:
    def __init__(self) -> None:
        self.manifest = None
        self.result = None

    def write_run(self, run_id, manifest, result):
        self.manifest = manifest
        self.result = result
        return Path("/tmp") / run_id


class BaselineScenarioTest(unittest.TestCase):
    def test_complete_baseline_reconciles_expected_operations_and_analytics(self) -> None:
        dataset = expected_dataset(42, 3)
        operations = _operations(dataset)
        analytics = _analytics(operations)
        summary = _summary(dataset)
        store = CapturingEvidenceStore()

        with self._common_patches(dataset, operations, store) as mocks:
            mocks["wait"].return_value = analytics
            mocks["summary"].return_value = summary

            run = baseline.execute_baseline(
                _lab_config(),
                records=3,
                seed=42,
                evidence_store=store,
                doctor_report=DoctorReport(checks=()),
                now=datetime(2026, 5, 15, tzinfo=timezone.utc),
            )

        self.assertEqual("PASS", run.status)
        self.assertIsNotNone(store.result)
        self.assertTrue(store.result["passed"])
        self.assertTrue(store.result["invariants"]["expectedMatchesOperations"])
        self.assertTrue(store.result["invariants"]["operationsMatchesAnalytics"])
        self.assertTrue(store.result["invariants"]["analyticsEventIdsUnique"])
        self.assertTrue(store.result["invariants"]["analyticsFreshAggregateVersionsAreOne"])
        self.assertTrue(store.result["invariants"]["analyticsSummaryMatchesExpected"])
        self.assertEqual(
            store.result["fingerprints"]["expectedBusinessFactsSha256"],
            store.result["fingerprints"]["operationsBusinessFactsSha256"],
        )
        self.assertEqual(
            store.result["fingerprints"]["operationsProjectionFactsSha256"],
            store.result["fingerprints"]["analyticsProjectionFactsSha256"],
        )
        self.assertEqual(300.0, store.manifest["workload"]["convergenceTimeoutSeconds"])


    def test_observability_capture_is_embedded_in_pass_evidence(self) -> None:
        dataset = expected_dataset(42, 3)
        operations = _operations(dataset)
        analytics = _analytics(operations)
        summary = _summary(dataset)
        store = CapturingEvidenceStore()
        before = _prometheus_snapshot(base=100.0)
        after = _prometheus_snapshot(base=100.0)
        for name, delta in expected_counter_deltas(
            customer_count=len(dataset.customers), invoice_count=len(dataset.invoices)
        ).items():
            after.counters[name] += delta

        with self._common_patches(dataset, operations, store) as mocks:
            mocks["wait"].return_value = analytics
            mocks["summary"].return_value = summary
            with patch.object(
                baseline, "_wait_for_stable_prometheus_snapshot", return_value=before
            ), patch.object(
                baseline, "_wait_for_prometheus_snapshot", return_value=after
            ):
                run = baseline.execute_baseline(
                    _lab_config(),
                    records=3,
                    seed=42,
                    evidence_store=store,
                    doctor_report=DoctorReport(checks=()),
                    now=datetime(2026, 5, 15, tzinfo=timezone.utc),
                    capture_observability=True,
                )

        self.assertEqual("PASS", run.status)
        self.assertEqual("prometheus-http-api", store.result["observability"]["source"])
        self.assertTrue(
            store.result["invariants"]["observabilityCounterDeltasMatchWorkload"]
        )
        self.assertTrue(store.result["invariants"]["observabilityPipelineDrained"])

    def test_custom_convergence_timeout_is_forwarded_and_recorded(self) -> None:
        dataset = expected_dataset(42, 3)
        operations = _operations(dataset)
        analytics = _analytics(operations)
        summary = _summary(dataset)
        store = CapturingEvidenceStore()

        with self._common_patches(dataset, operations, store) as mocks:
            mocks["wait"].return_value = analytics
            mocks["summary"].return_value = summary

            run = baseline.execute_baseline(
                _lab_config(),
                records=3,
                seed=42,
                evidence_store=store,
                doctor_report=DoctorReport(checks=()),
                now=datetime(2026, 5, 15, tzinfo=timezone.utc),
                convergence_timeout_seconds=1800.0,
            )

        self.assertEqual("PASS", run.status)
        self.assertEqual(1800.0, store.manifest["workload"]["convergenceTimeoutSeconds"])
        self.assertEqual(
            1800.0, mocks["wait"].call_args.kwargs["timeout_seconds"]
        )

    def test_analytics_non_convergence_is_fail_not_error(self) -> None:
        dataset = expected_dataset(42, 3)
        operations = _operations(dataset)
        store = CapturingEvidenceStore()

        with self._common_patches(dataset, operations, store) as mocks:
            mocks["wait"].side_effect = baseline.AnalyticsDidNotConverge("not converged")

            run = baseline.execute_baseline(
                _lab_config(),
                records=3,
                seed=42,
                evidence_store=store,
                doctor_report=DoctorReport(checks=()),
                now=datetime(2026, 5, 15, tzinfo=timezone.utc),
            )

        self.assertEqual("FAIL", run.status)
        self.assertFalse(store.result["passed"])
        self.assertEqual("analytics-convergence", store.result["phase"])
        self.assertNotIn("error", store.result)

    def test_source_dataset_mismatch_stops_before_business_mutation(self) -> None:
        dataset = expected_dataset(42, 3)
        store = CapturingEvidenceStore()
        control_auth = Mock()
        create_tenant = Mock()

        with (
            patch.object(baseline.BaselineConfig, "from_lab", return_value=_baseline_config()),
            patch.object(baseline, "repository_state", return_value=_repository_state()),
            patch.object(baseline, "environment_metadata", return_value={}),
            patch.object(baseline, "_capture_runtime_provenance", return_value={"captureStatus": "OK"}),
            patch.object(baseline, "_validate_evidence_compose"),
            patch.object(
                baseline,
                "_configure_mock_erp",
                side_effect=baseline.ScenarioError(
                    "preflight",
                    "MOCK_ERP_DATASET_MISMATCH",
                    "source does not match oracle",
                ),
            ),
            patch.object(baseline, "authenticate_lab_identity", control_auth),
            patch.object(baseline, "_create_tenant", create_tenant),
        ):
            run = baseline.execute_baseline(
                _lab_config(),
                records=3,
                seed=42,
                evidence_store=store,
                doctor_report=DoctorReport(checks=()),
                now=datetime(2026, 5, 15, tzinfo=timezone.utc),
            )

        self.assertEqual("ERROR", run.status)
        self.assertEqual("MOCK_ERP_DATASET_MISMATCH", store.result["error"]["code"])
        control_auth.assert_not_called()
        create_tenant.assert_not_called()

    def _common_patches(self, dataset, operations, store):
        customer_run = {
            "status": "COMPLETED",
            "statistics": {
                "fetched": len(dataset.customers),
                "accepted": len(dataset.customers),
                "rejected": 0,
                "duplicates": 0,
            },
        }
        invoice_run = {
            "status": "COMPLETED",
            "statistics": {
                "fetched": len(dataset.invoices),
                "accepted": len(dataset.invoices),
                "rejected": 0,
                "duplicates": 0,
            },
        }
        stack = _PatchStack()
        stack.add(patch.object(baseline.BaselineConfig, "from_lab", return_value=_baseline_config()))
        stack.add(patch.object(baseline, "repository_state", return_value=_repository_state()))
        stack.add(patch.object(baseline, "environment_metadata", return_value={}))
        stack.add(
            patch.object(
                baseline,
                "_capture_runtime_provenance",
                return_value={"captureStatus": "OK"},
            )
        )
        stack.add(patch.object(baseline, "_validate_evidence_compose"))
        stack.add(
            patch.object(
                baseline,
                "_configure_mock_erp",
                return_value={
                    "specVersion": dataset.sha256 and "eoc-deterministic-workload-v1",
                    "seed": dataset.seed,
                    "invoiceCount": len(dataset.invoices),
                    "customerCount": len(dataset.customers),
                    "datasetSha256": dataset.sha256,
                },
            )
        )
        stack.add(
            patch.object(
                baseline,
                "authenticate_lab_identity",
                return_value=SimpleNamespace(access_token="control-token"),
            )
        )
        stack.add(
            patch.object(
                baseline,
                "_authenticate_workload_identity",
                return_value=baseline.WorkloadIdentity(
                    "workload-token", "http://issuer", "workload", ()
                ),
            )
        )
        stack.add(
            patch.object(
                baseline, "_provision_workload_user", return_value=PLATFORM_USER_ID
            )
        )
        stack.add(
            patch.object(
                baseline, "_create_tenant", return_value=(TENANT_ID, "baseline-test")
            )
        )
        stack.add(patch.object(baseline, "_assign_workload_membership"))
        stack.add(
            patch.object(
                baseline, "_create_and_activate_connector", return_value=CONNECTOR_ID
            )
        )
        stack.add(
            patch.object(
                baseline,
                "_request_and_execute_import",
                side_effect=[customer_run, invoice_run],
            )
        )
        stack.add(patch.object(baseline, "_read_all_invoices", return_value=operations))
        stack.named["wait"] = stack.add(patch.object(baseline, "_wait_for_analytics"))
        stack.named["summary"] = stack.add(patch.object(baseline, "_read_summary"))
        return stack


class _PatchStack:
    def __init__(self) -> None:
        self.patchers = []
        self.started = []
        self.named = {}

    def add(self, patcher):
        self.patchers.append(patcher)
        return patcher

    def __enter__(self):
        for patcher in self.patchers:
            started = patcher.start()
            self.started.append(started)
        return self._named_started()

    def _named_started(self):
        result = {}
        for name, patcher in self.named.items():
            index = self.patchers.index(patcher)
            result[name] = self.started[index]
        return result

    def __exit__(self, exc_type, exc, tb):
        for patcher in reversed(self.patchers):
            patcher.stop()


def _lab_config():
    return SimpleNamespace(output_root=Path("/tmp/evidence"), repo_root=Path("/repo"))


def _baseline_config():
    return SimpleNamespace(
        lab=SimpleNamespace(lab_client_secret="control-secret"),
        workload_client_secret="workload-secret",
        prometheus_base_url="http://127.0.0.1:9090",
    )


def _prometheus_snapshot(*, base: float) -> PrometheusSnapshot:
    expected = expected_counter_deltas(customer_count=0, invoice_count=0)
    counters = {
        name: base
        for name in {
            *expected.keys(),
            "connectorOutboxDurationSamples",
            "operationsOutboxDurationSamples",
        }
    }
    gauges = {
        "connectorOutboxPending": 0.0,
        "operationsOutboxPending": 0.0,
        "connectorOutboxOldestSeconds": 0.0,
        "operationsOutboxOldestSeconds": 0.0,
    }
    return PrometheusSnapshot(counters=counters, gauges=gauges)


def _repository_state() -> RepositoryState:
    return RepositoryState(
        commit="c98b8a7",
        branch="feature/deterministic-workload-reconciliation",
        dirty=False,
        tracked_diff_sha256="0" * 64,
        untracked_files=(),
        untracked_content_sha256=None,
    )


def _operations(dataset):
    customer_ids = {}
    operations = []
    for index, invoice in enumerate(dataset.invoices, start=1):
        customer_ids.setdefault(
            invoice.customer_source_id,
            str(UUID(int=10_000 + len(customer_ids) + 1)),
        )
        total = invoice.total_amount
        open_amount = invoice.open_amount
        paid = f"{Decimal(total) - Decimal(open_amount):.2f}"
        operations.append(
            {
                "id": str(UUID(int=20_000 + index)),
                "tenantId": TENANT_ID,
                "customerId": customer_ids[invoice.customer_source_id],
                "invoiceNumber": invoice.invoice_number,
                "originalAmount": {"amount": total, "currency": invoice.currency},
                "paidAmount": {"amount": paid, "currency": invoice.currency},
                "openAmount": {"amount": open_amount, "currency": invoice.currency},
                "issueDate": invoice.issue_date,
                "dueDate": invoice.due_date,
                "businessDate": baseline.BUSINESS_DATE.isoformat(),
                "status": invoice.source_status,
                "cancelled": False,
                "overdue": False,
            }
        )
    return operations


def _analytics(operations):
    result = []
    for index, invoice in enumerate(operations, start=1):
        result.append(
            {
                "id": invoice["id"],
                "tenantId": TENANT_ID,
                "customer": {"id": invoice["customerId"], "projected": True},
                "invoiceNumber": invoice["invoiceNumber"],
                "originalAmount": invoice["originalAmount"],
                "paidAmount": invoice["paidAmount"],
                "outstandingAmount": invoice["openAmount"],
                "issueDate": invoice["issueDate"],
                "dueDate": invoice["dueDate"],
                "businessDate": invoice["businessDate"],
                "status": invoice["status"],
                "cancelled": invoice["cancelled"],
                "overdue": invoice["overdue"],
                "source": {
                    "eventId": str(UUID(int=30_000 + index)),
                    "aggregateVersion": 1,
                    "occurredAt": "2026-05-15T00:00:00Z",
                },
            }
        )
    return result


def _summary(dataset):
    value = expected_summary(dataset, baseline.BUSINESS_DATE)
    return {
        "tenantId": TENANT_ID,
        "businessDate": baseline.BUSINESS_DATE.isoformat(),
        **value,
    }


if __name__ == "__main__":
    unittest.main()
