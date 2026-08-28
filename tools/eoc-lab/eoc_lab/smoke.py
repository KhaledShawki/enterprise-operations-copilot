from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
import secrets
from typing import Any
from urllib.parse import urljoin, urlparse
from uuid import UUID

from eoc_lab.auth import authenticate_lab_identity
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
from eoc_lab.http import HttpClient, HttpResponse
from eoc_lab.process import CommandRunner
from eoc_lab.provenance import collect_runtime_provenance


SCENARIO_NAME = "smoke"
SCENARIO_VERSION = 2


@dataclass(frozen=True)
class SmokeRun:
    run_id: str
    run_directory: str
    status: str

    @property
    def passed(self) -> bool:
        return self.status == "PASS"


class ScenarioError(RuntimeError):
    def __init__(self, phase: str, code: str, message: str) -> None:
        super().__init__(message)
        self.phase = phase
        self.code = code


def execute_smoke(
    config: LabConfig,
    *,
    command_runner: CommandRunner | None = None,
    http_client: HttpClient | None = None,
    evidence_store: EvidenceStore | None = None,
    doctor_report: DoctorReport | None = None,
    now: datetime | None = None,
) -> SmokeRun:
    runner = command_runner or CommandRunner()
    http = http_client or HttpClient()
    store = evidence_store or EvidenceStore(config.output_root)
    started_at = now or utc_now()
    run_id = _run_id(started_at)

    # A run starts only after source provenance is known. If git state cannot be captured, the
    # harness cannot produce a trustworthy result and must fail before mutating the system.
    source_state = repository_state(config.repo_root, runner)
    host_environment = environment_metadata()
    report = doctor_report or run_doctor(config, command_runner=runner, http_client=http)
    runtime = _capture_runtime_provenance(config, runner)

    if not report.passed:
        failed_checks = [check.name for check in report.checks if not check.passed]
        return _write_terminal_run(
            config=config,
            store=store,
            source_state=source_state,
            host_environment=host_environment,
            runtime=runtime,
            report=report,
            run_id=run_id,
            started_at=started_at,
            status="ERROR",
            phase="preflight",
            error={
                "code": "DOCTOR_FAILED",
                "message": "Evidence lab preflight failed",
                "failedChecks": failed_checks,
            },
            metrics={"tenantsCreated": 0, "tenantsReadBack": 0},
            invariants=_base_invariants(report),
            resource=None,
        )

    if runtime.get("captureStatus") != "OK":
        return _write_terminal_run(
            config=config,
            store=store,
            source_state=source_state,
            host_environment=host_environment,
            runtime=runtime,
            report=report,
            run_id=run_id,
            started_at=started_at,
            status="ERROR",
            phase="preflight",
            error={
                "code": "RUNTIME_PROVENANCE_UNAVAILABLE",
                "message": "Runtime provenance could not be captured",
            },
            metrics={"tenantsCreated": 0, "tenantsReadBack": 0},
            invariants=_base_invariants(report),
            resource=None,
        )

    metrics = {"tenantsCreated": 0, "tenantsReadBack": 0}
    invariants = _base_invariants(report)
    resource: dict[str, str] | None = None
    phase = "authentication"

    try:
        identity = authenticate_lab_identity(config, http)
        invariants["authenticatedPlatformAdmin"] = True

        phase = "tenant-create"
        tenant_key = f"lab-{started_at.strftime('%Y%m%d%H%M%S')}-{secrets.token_hex(4)}"
        display_name = f"EOC Lab Smoke {run_id}"
        create_url = f"{config.platform_base_url}/api/v1/tenants"
        create_response = http.request(
            "POST",
            create_url,
            headers={"Authorization": f"Bearer {identity.access_token}"},
            json_body={"tenantKey": tenant_key, "displayName": display_name},
        )
        created, location_url = _created_tenant_contract(
            create_response,
            request_url=create_url,
            platform_base_url=config.platform_base_url,
            tenant_key=tenant_key,
            display_name=display_name,
        )
        metrics["tenantsCreated"] = 1
        resource = {"tenantId": created["id"], "tenantKey": tenant_key}
        invariants["createLocationMatchesResource"] = True

        phase = "tenant-read-back"
        get_response = http.request(
            "GET",
            location_url,
            headers={"Authorization": f"Bearer {identity.access_token}"},
        )
        persisted = _read_tenant_contract(get_response)
        metrics["tenantsReadBack"] = 1

        expected = {
            "id": created["id"],
            "tenantKey": tenant_key,
            "displayName": display_name,
            "status": "ACTIVE",
        }
        matches = created == expected and persisted == expected
        invariants["createdTenantMatchesReadBack"] = matches

        return _write_terminal_run(
            config=config,
            store=store,
            source_state=source_state,
            host_environment=host_environment,
            runtime=runtime,
            report=report,
            run_id=run_id,
            started_at=started_at,
            status="PASS" if matches else "FAIL",
            phase="complete",
            error=None,
            metrics=metrics,
            invariants=invariants,
            resource=resource,
        )
    except Exception as exception:  # scenario failures must remain visible as evidence
        if isinstance(exception, ScenarioError):
            phase = exception.phase
            error = {"code": exception.code, "message": str(exception)}
        else:
            error = {
                "code": "UNEXPECTED_EXCEPTION",
                "message": _redact(str(exception) or type(exception).__name__, config),
                "errorType": type(exception).__name__,
            }

        return _write_terminal_run(
            config=config,
            store=store,
            source_state=source_state,
            host_environment=host_environment,
            runtime=runtime,
            report=report,
            run_id=run_id,
            started_at=started_at,
            status="ERROR",
            phase=phase,
            error=error,
            metrics=metrics,
            invariants=invariants,
            resource=resource,
        )


def _created_tenant_contract(
    response: HttpResponse,
    *,
    request_url: str,
    platform_base_url: str,
    tenant_key: str,
    display_name: str,
) -> tuple[dict[str, str], str]:
    if response.status != 201:
        raise ScenarioError("tenant-create", "UNEXPECTED_HTTP_STATUS", f"Expected HTTP 201, received HTTP {response.status}")
    if response.content_type != "application/json":
        raise ScenarioError(
            "tenant-create",
            "UNEXPECTED_CONTENT_TYPE",
            f"Expected application/json, received {response.content_type!r}",
        )

    payload = _tenant_projection(response, phase="tenant-create")
    if payload["tenantKey"] != tenant_key or payload["displayName"] != display_name:
        raise ScenarioError("tenant-create", "CREATE_RESPONSE_MISMATCH", "Created tenant response does not match the request")
    if payload["status"] != "ACTIVE":
        raise ScenarioError("tenant-create", "UNEXPECTED_TENANT_STATUS", "Created tenant is not ACTIVE")

    location = response.headers.get("location")
    if not location:
        raise ScenarioError("tenant-create", "MISSING_LOCATION", "Tenant creation response is missing Location")
    location_url = urljoin(request_url, location)
    expected_location = f"{platform_base_url}/api/v1/tenants/{payload['id']}"
    if _normalized_url(location_url) != _normalized_url(expected_location):
        raise ScenarioError("tenant-create", "LOCATION_MISMATCH", "Location does not identify the created tenant")
    return payload, location_url


def _read_tenant_contract(response: HttpResponse) -> dict[str, str]:
    if response.status != 200:
        raise ScenarioError("tenant-read-back", "UNEXPECTED_HTTP_STATUS", f"Expected HTTP 200, received HTTP {response.status}")
    if response.content_type != "application/json":
        raise ScenarioError(
            "tenant-read-back",
            "UNEXPECTED_CONTENT_TYPE",
            f"Expected application/json, received {response.content_type!r}",
        )
    return _tenant_projection(response, phase="tenant-read-back")


def _tenant_projection(response: HttpResponse, *, phase: str) -> dict[str, str]:
    try:
        payload: Any = response.json()
    except ValueError as exception:
        raise ScenarioError(phase, "INVALID_JSON", "Tenant response is not valid JSON") from exception
    if not isinstance(payload, dict):
        raise ScenarioError(phase, "INVALID_RESPONSE_SHAPE", "Tenant response is not a JSON object")

    tenant_id = payload.get("id")
    tenant_key = payload.get("tenantKey")
    display_name = payload.get("displayName")
    status = payload.get("status")
    if not all(isinstance(value, str) and value for value in (tenant_id, tenant_key, display_name, status)):
        raise ScenarioError(phase, "INVALID_RESPONSE_SHAPE", "Tenant response is missing required string fields")
    try:
        UUID(tenant_id)
    except ValueError as exception:
        raise ScenarioError(phase, "INVALID_TENANT_ID", "Tenant response id is not a UUID") from exception

    # Project only the stable fields required by this scenario. Additive v1 response fields are
    # intentionally ignored so the lab does not turn compatible API evolution into a false failure.
    return {
        "id": tenant_id,
        "tenantKey": tenant_key,
        "displayName": display_name,
        "status": status,
    }


def _capture_runtime_provenance(config: LabConfig, runner: CommandRunner) -> dict[str, Any]:
    try:
        return collect_runtime_provenance(config, runner)
    except Exception as exception:
        return {
            "captureStatus": "ERROR",
            "errorType": type(exception).__name__,
            "message": _redact(str(exception) or type(exception).__name__, config),
        }


def _write_terminal_run(
    *,
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
    error: dict[str, Any] | None,
    metrics: dict[str, int],
    invariants: dict[str, bool],
    resource: dict[str, str] | None,
) -> SmokeRun:
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
        "configuration": {
            "envFile": _display_path(config.env_file, config.repo_root),
            "platformBaseUrl": config.platform_base_url,
            "keycloakBaseUrl": config.keycloak_base_url,
            "expectedIssuer": config.expected_issuer,
            "labClientId": config.lab_client_id,
        },
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
        "metrics": metrics,
        "invariants": invariants,
    }
    if error is not None:
        result["error"] = error
    if resource is not None:
        result["resource"] = resource

    run_directory = store.write_run(run_id, manifest, result)
    return SmokeRun(run_id=run_id, run_directory=str(run_directory), status=status)


def _base_invariants(report: DoctorReport) -> dict[str, bool]:
    checks = {check.name: check.passed for check in report.checks}
    return {
        "doctorPassed": report.passed,
        "anonymousProtectedApiRejected": checks.get("anonymous-api-rejected", False),
        "authenticatedPlatformAdmin": False,
        "createLocationMatchesResource": False,
        "createdTenantMatchesReadBack": False,
    }


def _run_id(started_at: datetime) -> str:
    return f"{started_at.strftime('%Y%m%dT%H%M%SZ')}-smoke-{secrets.token_hex(3)}"


def _display_path(path, repo_root):
    try:
        return str(path.relative_to(repo_root))
    except ValueError:
        return str(path)


def _normalized_url(value: str) -> tuple[str, str, str]:
    parsed = urlparse(value)
    return parsed.scheme.lower(), parsed.netloc.lower(), parsed.path.rstrip("/")


def _redact(value: str, config: LabConfig) -> str:
    if not value:
        return value
    return value.replace(config.lab_client_secret, "<redacted>")
