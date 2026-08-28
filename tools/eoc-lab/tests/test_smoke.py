from datetime import datetime, timezone
import json
from pathlib import Path
import tempfile
import unittest

from eoc_lab.config import LabConfig
from eoc_lab.doctor import DoctorReport
from eoc_lab.evidence import CheckResult, EvidenceStore
from eoc_lab.http import HttpResponse
from eoc_lab.process import CommandResult
from eoc_lab.smoke import execute_smoke


TENANT_ID = "11111111-1111-1111-1111-111111111111"


class FakeCommandRunner:
    def run(self, argv, *, timeout_seconds=30.0):
        command = tuple(argv)
        if command[-2:] == ("rev-parse", "HEAD"):
            return CommandResult(command, 0, "abc123", "")
        if "symbolic-ref" in command:
            return CommandResult(command, 0, "feature/evidence-lab", "")
        if "status" in command:
            return CommandResult(command, 0, "", "")
        if "diff" in command:
            return CommandResult(command, 0, "", "")
        if "ls-files" in command:
            return CommandResult(command, 0, "", "")
        if command[:2] == ("docker", "version"):
            return CommandResult(command, 0, "28.3.1", "")
        if command[:3] == ("docker", "compose", "version"):
            return CommandResult(command, 0, "2.39.1", "")
        if "ps" in command and "--quiet" in command:
            return CommandResult(command, 0, f"container-{command[-1]}", "")
        if command[:2] == ("docker", "inspect"):
            service = command[-1].removeprefix("container-")
            return CommandResult(command, 0, f"image/{service}:local|sha256:{service}", "")
        raise AssertionError(f"Unexpected command: {command}")


class FakeHttpClient:
    def __init__(self, *, create_status: int = 201, persisted_override=None) -> None:
        self.created_tenant = None
        self.create_status = create_status
        self.persisted_override = persisted_override

    def request(self, method, url, **kwargs):
        if url.endswith("/protocol/openid-connect/token"):
            form = kwargs["form_body"]
            if form["client_secret"] != "super-secret":
                raise AssertionError("Unexpected client secret")
            return response(200, {"access_token": "access-token"})
        if url.endswith("/api/v1/me"):
            return response(
                200,
                {
                    "issuer": "http://localhost:8180/realms/eoc",
                    "subject": "lab",
                    "roles": ["platform-admin"],
                },
            )
        if method == "POST" and url.endswith("/api/v1/tenants"):
            if self.create_status != 201:
                return response(self.create_status, {"code": "simulated"})
            body = kwargs["json_body"]
            self.created_tenant = {
                "id": TENANT_ID,
                "tenantKey": body["tenantKey"],
                "displayName": body["displayName"],
                "status": "ACTIVE",
                "compatibleFutureField": "ignored",
            }
            return response(
                201,
                self.created_tenant,
                headers={"location": f"http://127.0.0.1:8080/api/v1/tenants/{TENANT_ID}"},
            )
        if method == "GET" and url.endswith(f"/api/v1/tenants/{TENANT_ID}"):
            payload = dict(self.created_tenant)
            if self.persisted_override:
                payload.update(self.persisted_override)
            return response(200, payload)
        raise AssertionError(f"Unexpected HTTP request: {method} {url}")


class SmokeScenarioTest(unittest.TestCase):
    def test_writes_successful_evidence_without_credentials_and_allows_additive_fields(self) -> None:
        with lab_environment() as (config, doctor):
            run = execute_smoke(
                config,
                command_runner=FakeCommandRunner(),
                http_client=FakeHttpClient(),
                evidence_store=EvidenceStore(config.output_root),
                doctor_report=doctor,
                now=datetime(2026, 8, 28, 12, 0, 0, tzinfo=timezone.utc),
            )

            self.assertTrue(run.passed)
            self.assertEqual("PASS", run.status)
            result, manifest_text, result_text = read_evidence(run.run_directory)
            self.assertEqual("complete", result["phase"])
            self.assertTrue(result["invariants"]["createLocationMatchesResource"])
            self.assertTrue(result["invariants"]["createdTenantMatchesReadBack"])
            self.assertNotIn("super-secret", manifest_text)
            self.assertNotIn("super-secret", result_text)
            self.assertNotIn("access-token", result_text)

    def test_persists_http_failure_as_error_evidence(self) -> None:
        with lab_environment() as (config, doctor):
            run = execute_smoke(
                config,
                command_runner=FakeCommandRunner(),
                http_client=FakeHttpClient(create_status=503),
                evidence_store=EvidenceStore(config.output_root),
                doctor_report=doctor,
                now=datetime(2026, 8, 28, 12, 0, 0, tzinfo=timezone.utc),
            )

            self.assertEqual("ERROR", run.status)
            result, _, _ = read_evidence(run.run_directory)
            self.assertEqual("tenant-create", result["phase"])
            self.assertEqual("UNEXPECTED_HTTP_STATUS", result["error"]["code"])
            self.assertEqual(0, result["metrics"]["tenantsCreated"])

    def test_persists_doctor_failure_without_mutating_business_state(self) -> None:
        with lab_environment(doctor_passed=False) as (config, doctor):
            http = FakeHttpClient()
            run = execute_smoke(
                config,
                command_runner=FakeCommandRunner(),
                http_client=http,
                evidence_store=EvidenceStore(config.output_root),
                doctor_report=doctor,
                now=datetime(2026, 8, 28, 12, 0, 0, tzinfo=timezone.utc),
            )

            self.assertEqual("ERROR", run.status)
            result, _, _ = read_evidence(run.run_directory)
            self.assertEqual("preflight", result["phase"])
            self.assertEqual("DOCTOR_FAILED", result["error"]["code"])
            self.assertEqual(0, result["metrics"]["tenantsCreated"])
            self.assertIsNone(http.created_tenant)

    def test_semantic_read_back_mismatch_is_fail_not_infrastructure_error(self) -> None:
        with lab_environment() as (config, doctor):
            run = execute_smoke(
                config,
                command_runner=FakeCommandRunner(),
                http_client=FakeHttpClient(persisted_override={"displayName": "wrong"}),
                evidence_store=EvidenceStore(config.output_root),
                doctor_report=doctor,
                now=datetime(2026, 8, 28, 12, 0, 0, tzinfo=timezone.utc),
            )

            self.assertEqual("FAIL", run.status)
            result, _, _ = read_evidence(run.run_directory)
            self.assertFalse(result["invariants"]["createdTenantMatchesReadBack"])
            self.assertNotIn("error", result)


class lab_environment:
    def __init__(self, *, doctor_passed: bool = True) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.doctor_passed = doctor_passed

    def __enter__(self):
        root = Path(self.temporary.name)
        compose_file = root / "deployment/compose/compose.yaml"
        compose_file.parent.mkdir(parents=True)
        compose_file.write_text("services: {}\n", encoding="utf-8")
        env_file = root / "deployment/compose/.env"
        env_file.write_text("unused=true\n", encoding="utf-8")
        config = LabConfig(
            repo_root=root,
            env_file=env_file,
            output_root=root / "evidence/runs",
            platform_base_url="http://127.0.0.1:8080",
            keycloak_base_url="http://127.0.0.1:8180",
            expected_issuer="http://localhost:8180/realms/eoc",
            lab_client_id="eoc-lab",
            lab_client_secret="super-secret",
            connector_topic="connector",
            connector_dlt_topic="connector.dlt",
            operations_topic="operations",
            analytics_dlt_topic="analytics.dlt",
            compose_file=compose_file,
        )
        doctor = DoctorReport(
            (
                CheckResult("anonymous-api-rejected", self.doctor_passed, "HTTP 401"),
                CheckResult("lab-authentication", self.doctor_passed, "ok"),
            )
        )
        return config, doctor

    def __exit__(self, exc_type, exc_value, traceback):
        self.temporary.cleanup()


def read_evidence(run_directory: str):
    directory = Path(run_directory)
    result = json.loads((directory / "result.json").read_text(encoding="utf-8"))
    return (
        result,
        (directory / "manifest.json").read_text(encoding="utf-8"),
        (directory / "result.json").read_text(encoding="utf-8"),
    )


def response(status: int, payload: object, *, headers=None) -> HttpResponse:
    response_headers = {"content-type": "application/json"}
    response_headers.update(headers or {})
    return HttpResponse(
        status=status,
        headers=response_headers,
        body=json.dumps(payload).encode("utf-8"),
    )


if __name__ == "__main__":
    unittest.main()
