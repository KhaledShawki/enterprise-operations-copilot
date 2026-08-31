from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
import secrets
from typing import Any

from eoc_lab.auth import authenticate_lab_identity
from eoc_lab.baseline_api import (
    BUSINESS_DATE,
    PAGE_SIZE,
    assign_workload_membership as _assign_workload_membership,
    authenticate_workload_identity as _authenticate_workload_identity,
    create_and_activate_connector as _create_and_activate_connector,
    create_tenant as _create_tenant,
    import_statistics as _import_statistics,
    provision_workload_user as _provision_workload_user,
    read_all_invoices as _read_all_invoices,
    read_summary as _read_summary,
    request_and_execute_import as _request_and_execute_import,
    wait_for_analytics as _wait_for_analytics,
)
from eoc_lab.baseline_config import BaselineConfig
from eoc_lab.baseline_dataset import SPEC_VERSION, ExpectedDataset, expected_dataset
from eoc_lab.baseline_runtime import (
    capture_runtime_provenance as _capture_runtime_provenance,
    configure_mock_erp as _configure_mock_erp,
    redact as _redact,
    validate_evidence_compose as _validate_evidence_compose,
)
from eoc_lab.baseline_types import AnalyticsDidNotConverge, ScenarioError, WorkloadIdentity
from eoc_lab.config import LabConfig
from eoc_lab.doctor import DoctorReport, run_doctor
from eoc_lab.evidence import (
    SCHEMA_VERSION,
    EvidenceStore,
    RepositoryState,
    checks_as_dict,
    environment_metadata,
    isoformat_utc,
    repository_state,
    utc_now,
)
from eoc_lab.http import HttpClient
from eoc_lab.process import CommandRunner
from eoc_lab.reconciliation import (
    ReconciliationError,
    analytics_projection_facts,
    canonical_hash,
    expected_business_facts,
    expected_summary,
    lineage_invariants,
    normalize_summary,
    operations_business_facts,
    operations_projection_facts,
)


SCENARIO_NAME = "baseline"
SCENARIO_VERSION = 1


@dataclass(frozen=True)
class BaselineRun:
    run_id: str
    run_directory: str
    status: str

    @property
    def passed(self) -> bool:
        return self.status == "PASS"


def execute_baseline(
    config: LabConfig,
    *,
    records: int,
    seed: int,
    command_runner: CommandRunner | None = None,
    http_client: HttpClient | None = None,
    evidence_store: EvidenceStore | None = None,
    doctor_report: DoctorReport | None = None,
    now: datetime | None = None,
) -> BaselineRun:
    baseline_config = BaselineConfig.from_lab(config)
    expected = expected_dataset(seed, records)
    runner = command_runner or CommandRunner()
    http = http_client or HttpClient()
    store = evidence_store or EvidenceStore(config.output_root)
    started_at = now or utc_now()
    run_id = _run_id(started_at)

    source_state = repository_state(config.repo_root, runner)
    host_environment = environment_metadata()
    report = doctor_report or run_doctor(config, command_runner=runner, http_client=http)
    runtime = _capture_runtime_provenance(baseline_config, runner)
    state = _initial_state(report)
    metadata: dict[str, Any] | None = None
    phase = "preflight"

    try:
        _require_preflight(report, runtime)
        _validate_evidence_compose(baseline_config, runner)
        state.invariants["evidenceComposeValid"] = True
        metadata = _configure_mock_erp(baseline_config, runner, expected, records, seed)
        state.invariants["mockErpDatasetMatchesExpectation"] = True

        phase = "authentication"
        control = authenticate_lab_identity(config, http)
        workload = _authenticate_workload_identity(baseline_config, http)
        if "platform-admin" in workload.roles:
            raise ScenarioError(
                phase, "WORKLOAD_HAS_PLATFORM_ADMIN", "Workload identity must not have platform-admin"
            )
        state.invariants["workloadIdentityNotPlatformAdmin"] = True

        phase = "workload-user-provisioning"
        platform_user_id = _provision_workload_user(config, http, workload)

        phase = "tenant-create"
        tenant_id, tenant_key = _create_tenant(config, http, control.access_token, run_id)
        state.resource = {"tenantId": tenant_id, "tenantKey": tenant_key}

        phase = "tenant-membership"
        _assign_workload_membership(
            config, http, control.access_token, tenant_id, platform_user_id
        )
        state.invariants["tenantScopedMembershipAssigned"] = True

        phase = "connector-create"
        connector_id = _create_and_activate_connector(config, http, workload.access_token, tenant_id)
        state.resource["connectorId"] = connector_id

        _run_imports(config, http, workload, tenant_id, connector_id, expected, state)
        operations = _reconcile_operations(config, http, workload, tenant_id, expected, state)
        if not state.invariants["expectedMatchesOperations"]:
            return _terminal_run(
                config, store, source_state, host_environment, runtime, report, run_id, started_at,
                "FAIL", "operations-reconciliation", state, expected, metadata
            )

        _reconcile_analytics(config, http, workload, tenant_id, expected, operations, state)
        passed = all(state.invariants.values())
        return _terminal_run(
            config, store, source_state, host_environment, runtime, report, run_id, started_at,
            "PASS" if passed else "FAIL", "complete", state, expected, metadata
        )
    except AnalyticsDidNotConverge:
        return _terminal_run(
            config, store, source_state, host_environment, runtime, report, run_id, started_at,
            "FAIL", "analytics-convergence", state, expected, metadata
        )
    except Exception as exception:
        error = _error_payload(exception, phase, baseline_config)
        return _terminal_run(
            config, store, source_state, host_environment, runtime, report, run_id, started_at,
            "ERROR", getattr(exception, "phase", phase), state, expected, metadata, error=error
        )


@dataclass
class _ScenarioState:
    metrics: dict[str, int]
    invariants: dict[str, bool]
    fingerprints: dict[str, str]
    resource: dict[str, str] | None = None


def _initial_state(report: DoctorReport) -> _ScenarioState:
    return _ScenarioState(
        metrics={
            "customersImported": 0,
            "invoicesImported": 0,
            "operationsInvoices": 0,
            "analyticsReceivables": 0,
        },
        invariants={
            "doctorPassed": report.passed,
            "evidenceComposeValid": False,
            "mockErpDatasetMatchesExpectation": False,
            "workloadIdentityNotPlatformAdmin": False,
            "tenantScopedMembershipAssigned": False,
            "customerImportCompleted": False,
            "invoiceImportCompleted": False,
            "expectedMatchesOperations": False,
            "operationsMatchesAnalytics": False,
            "analyticsEventIdsUnique": False,
            "analyticsFreshAggregateVersionsAreOne": False,
            "analyticsSummaryMatchesExpected": False,
        },
        fingerprints={},
    )


def _require_preflight(report: DoctorReport, runtime: dict[str, Any]) -> None:
    if not report.passed:
        raise ScenarioError("preflight", "DOCTOR_FAILED", "Evidence lab preflight failed")
    if runtime.get("captureStatus") != "OK":
        raise ScenarioError(
            "preflight",
            "RUNTIME_PROVENANCE_UNAVAILABLE",
            "Baseline runtime provenance could not be captured",
        )


def _run_imports(
    config: LabConfig,
    http: HttpClient,
    workload: WorkloadIdentity,
    tenant_id: str,
    connector_id: str,
    expected: ExpectedDataset,
    state: _ScenarioState,
) -> None:
    customer_run = _request_and_execute_import(
        config, http, workload.access_token, tenant_id, connector_id, "CUSTOMERS"
    )
    customer_stats = _import_statistics(customer_run)
    state.metrics["customersImported"] = customer_stats["accepted"]
    state.invariants["customerImportCompleted"] = (
        customer_run.get("status") == "COMPLETED"
        and customer_stats["fetched"] == len(expected.customers)
        and customer_stats["accepted"] == len(expected.customers)
        and customer_stats["rejected"] == 0
    )
    if not state.invariants["customerImportCompleted"]:
        raise ScenarioError(
            "customer-import", "CUSTOMER_IMPORT_MISMATCH", "Customer import did not complete exactly"
        )

    invoice_run = _request_and_execute_import(
        config, http, workload.access_token, tenant_id, connector_id, "INVOICES"
    )
    invoice_stats = _import_statistics(invoice_run)
    state.metrics["invoicesImported"] = invoice_stats["accepted"]
    state.invariants["invoiceImportCompleted"] = (
        invoice_run.get("status") == "COMPLETED"
        and invoice_stats["fetched"] == len(expected.invoices)
        and invoice_stats["accepted"] == len(expected.invoices)
        and invoice_stats["rejected"] == 0
    )
    if not state.invariants["invoiceImportCompleted"]:
        raise ScenarioError(
            "invoice-import", "INVOICE_IMPORT_MISMATCH", "Invoice import did not complete exactly"
        )


def _reconcile_operations(
    config: LabConfig,
    http: HttpClient,
    workload: WorkloadIdentity,
    tenant_id: str,
    expected: ExpectedDataset,
    state: _ScenarioState,
) -> list[dict[str, Any]]:
    operations = _read_all_invoices(config, http, workload.access_token, tenant_id)
    state.metrics["operationsInvoices"] = len(operations)
    expected_facts = expected_business_facts(expected)
    operations_facts = operations_business_facts(operations)
    state.fingerprints["expectedBusinessFactsSha256"] = canonical_hash(expected_facts)
    state.fingerprints["operationsBusinessFactsSha256"] = canonical_hash(operations_facts)
    state.invariants["expectedMatchesOperations"] = expected_facts == operations_facts
    return operations


def _reconcile_analytics(
    config: LabConfig,
    http: HttpClient,
    workload: WorkloadIdentity,
    tenant_id: str,
    expected: ExpectedDataset,
    operations: list[dict[str, Any]],
    state: _ScenarioState,
) -> None:
    analytics = _wait_for_analytics(
        config, http, workload.access_token, tenant_id, expected_count=len(expected.invoices)
    )
    state.metrics["analyticsReceivables"] = len(analytics)

    operations_projection = operations_projection_facts(operations)
    analytics_projection = analytics_projection_facts(analytics)
    state.fingerprints["operationsProjectionFactsSha256"] = canonical_hash(operations_projection)
    state.fingerprints["analyticsProjectionFactsSha256"] = canonical_hash(analytics_projection)
    state.invariants["operationsMatchesAnalytics"] = operations_projection == analytics_projection
    state.invariants.update(lineage_invariants(analytics))

    summary_payload = _read_summary(config, http, workload.access_token, tenant_id)
    actual_summary = normalize_summary(
        summary_payload, tenant_id=tenant_id, business_date=BUSINESS_DATE.isoformat()
    )
    expected_summary_value = expected_summary(expected, BUSINESS_DATE)
    state.fingerprints["expectedSummarySha256"] = canonical_hash([expected_summary_value])
    state.fingerprints["analyticsSummarySha256"] = canonical_hash([actual_summary])
    state.invariants["analyticsSummaryMatchesExpected"] = actual_summary == expected_summary_value


def _error_payload(exception: Exception, phase: str, config: BaselineConfig) -> dict[str, Any]:
    if isinstance(exception, ScenarioError):
        return {"code": exception.code, "message": str(exception)}
    if isinstance(exception, ReconciliationError):
        return {"code": "INVALID_RECONCILIATION_INPUT", "message": str(exception)}
    return {
        "code": "UNEXPECTED_EXCEPTION",
        "message": _redact(str(exception) or type(exception).__name__, config),
        "errorType": type(exception).__name__,
    }


def _terminal_run(
    config: LabConfig,
    store: EvidenceStore,
    source_state: RepositoryState,
    host_environment: dict[str, Any],
    runtime: dict[str, Any],
    report: DoctorReport,
    run_id: str,
    started_at: datetime,
    status: str,
    phase: str,
    state: _ScenarioState,
    expected: ExpectedDataset,
    metadata: dict[str, Any] | None,
    *,
    error: dict[str, Any] | None = None,
) -> BaselineRun:
    finished_at = utc_now()
    manifest = {
        "schemaVersion": SCHEMA_VERSION,
        "runId": run_id,
        "scenario": SCENARIO_NAME,
        "scenarioVersion": SCENARIO_VERSION,
        "git": {
            "commit": source_state.commit,
            "branch": source_state.branch,
            "dirty": source_state.dirty,
            "trackedDiffSha256": source_state.tracked_diff_sha256,
            "untrackedFiles": list(source_state.untracked_files),
            "untrackedContentSha256": source_state.untracked_content_sha256,
        },
        "startedAt": isoformat_utc(started_at),
        "finishedAt": isoformat_utc(finished_at),
        "environment": host_environment,
        "runtime": runtime,
        "workload": _workload_manifest(expected, metadata),
    }
    result: dict[str, Any] = {
        "schemaVersion": SCHEMA_VERSION,
        "runId": run_id,
        "scenario": SCENARIO_NAME,
        "scenarioVersion": SCENARIO_VERSION,
        "status": status,
        "passed": status == "PASS",
        "phase": phase,
        "checks": checks_as_dict(report.checks),
        "metrics": state.metrics,
        "invariants": state.invariants,
        "fingerprints": state.fingerprints,
    }
    if state.resource is not None:
        result["resource"] = state.resource
    if error is not None:
        result["error"] = error
    directory = store.write_run(run_id, manifest, result)
    return BaselineRun(run_id, str(directory), status)


def _workload_manifest(expected: ExpectedDataset, metadata: dict[str, Any] | None) -> dict[str, Any]:
    return {
        "specVersion": SPEC_VERSION,
        "seed": expected.seed,
        "records": len(expected.invoices),
        "customerCount": len(expected.customers),
        "expectedDatasetSha256": expected.sha256,
        "mockErpMetadata": metadata,
        "businessDate": BUSINESS_DATE.isoformat(),
        "pageSize": PAGE_SIZE,
    }


def _run_id(started_at: datetime) -> str:
    return f"{started_at.strftime('%Y%m%dT%H%M%SZ')}-baseline-{secrets.token_hex(3)}"
