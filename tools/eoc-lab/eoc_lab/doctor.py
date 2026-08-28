from __future__ import annotations

from dataclasses import dataclass
import re
from typing import Callable

from eoc_lab.auth import authenticate_lab_identity
from eoc_lab.config import LabConfig
from eoc_lab.evidence import CheckResult
from eoc_lab.http import HttpClient
from eoc_lab.process import CommandRunner


EXPECTED_PARTITIONS = 6
REQUIRED_RUNNING_SERVICES = frozenset(
    {
        "platform-postgres",
        "keycloak-postgres",
        "keycloak",
        "kafka",
        "platform-service",
        "web-bff",
    }
)


@dataclass(frozen=True)
class DoctorReport:
    checks: tuple[CheckResult, ...]

    @property
    def passed(self) -> bool:
        return all(check.passed for check in self.checks)


def run_doctor(
    config: LabConfig,
    *,
    command_runner: CommandRunner | None = None,
    http_client: HttpClient | None = None,
) -> DoctorReport:
    runner = command_runner or CommandRunner()
    http = http_client or HttpClient()
    checks: list[CheckResult] = []

    def capture(name: str, operation: Callable[[], str]) -> None:
        try:
            detail = operation()
            checks.append(CheckResult(name=name, passed=True, detail=detail))
        except Exception as exception:  # noqa: BLE001 - doctor must report all failed checks
            checks.append(CheckResult(name=name, passed=False, detail=str(exception)))

    capture("docker-daemon", lambda: _docker_version(runner))
    capture("compose-configuration", lambda: _compose_configuration(config, runner))
    capture("compose-services", lambda: _running_services(config, runner))
    capture("platform-liveness", lambda: _health(http, config.platform_base_url, "liveness"))
    capture("platform-readiness", lambda: _health(http, config.platform_base_url, "readiness"))
    capture("keycloak-discovery", lambda: _keycloak_discovery(http, config))
    capture("anonymous-api-rejected", lambda: _anonymous_api_rejected(http, config))
    capture("lab-authentication", lambda: _lab_authentication(http, config))

    for topic_name in (
        config.connector_topic,
        config.connector_dlt_topic,
        config.operations_topic,
        config.analytics_dlt_topic,
    ):
        capture(
            f"kafka-topic:{topic_name}",
            lambda topic=topic_name: _kafka_topic(config, runner, topic),
        )

    return DoctorReport(checks=tuple(checks))


def _docker_version(runner: CommandRunner) -> str:
    result = runner.run(["docker", "version", "--format", "{{.Server.Version}}"])
    if result.returncode != 0 or not result.stdout:
        raise RuntimeError(result.stderr or "Docker daemon is unavailable")
    return f"server={result.stdout}"


def _compose_configuration(config: LabConfig, runner: CommandRunner) -> str:
    result = runner.run([*config.compose_prefix, "config", "--quiet"])
    if result.returncode != 0:
        raise RuntimeError(result.stderr or result.stdout or "Docker Compose configuration is invalid")
    return "valid"


def _running_services(config: LabConfig, runner: CommandRunner) -> str:
    result = runner.run([*config.compose_prefix, "ps", "--status", "running", "--services"])
    if result.returncode != 0:
        raise RuntimeError(result.stderr or result.stdout or "Cannot inspect Docker Compose services")

    running = {line.strip() for line in result.stdout.splitlines() if line.strip()}
    missing = sorted(REQUIRED_RUNNING_SERVICES - running)
    if missing:
        raise RuntimeError(f"Required Compose services are not running: {', '.join(missing)}")
    return f"running={','.join(sorted(REQUIRED_RUNNING_SERVICES))}"


def _health(http: HttpClient, base_url: str, probe: str) -> str:
    response = http.request("GET", f"{base_url}/actuator/health/{probe}")
    if response.status != 200:
        raise RuntimeError(f"HTTP {response.status}")
    payload = response.json()
    if not isinstance(payload, dict) or payload.get("status") != "UP":
        raise RuntimeError(f"Unexpected health response: {payload!r}")
    return "UP"


def _keycloak_discovery(http: HttpClient, config: LabConfig) -> str:
    response = http.request(
        "GET", f"{config.keycloak_base_url}/realms/eoc/.well-known/openid-configuration"
    )
    if response.status != 200:
        raise RuntimeError(f"HTTP {response.status}")
    payload = response.json()
    if not isinstance(payload, dict) or payload.get("issuer") != config.expected_issuer:
        raise RuntimeError(f"Unexpected issuer: {payload!r}")
    return f"issuer={config.expected_issuer}"


def _anonymous_api_rejected(http: HttpClient, config: LabConfig) -> str:
    response = http.request("GET", f"{config.platform_base_url}/api/v1/me")
    if response.status != 401:
        raise RuntimeError(f"Expected HTTP 401, received HTTP {response.status}")
    return "HTTP 401"


def _lab_authentication(http: HttpClient, config: LabConfig) -> str:
    identity = authenticate_lab_identity(config, http)
    return f"subject={identity.subject};roles={','.join(identity.roles)}"


def _kafka_topic(config: LabConfig, runner: CommandRunner, topic_name: str) -> str:
    result = runner.run(
        [
            *config.compose_prefix,
            "exec",
            "-T",
            "kafka",
            "/opt/kafka/bin/kafka-topics.sh",
            "--bootstrap-server",
            "kafka:9092",
            "--describe",
            "--topic",
            topic_name,
        ]
    )
    if result.returncode != 0:
        raise RuntimeError(result.stderr or result.stdout or f"Cannot describe Kafka topic {topic_name}")

    partition_count_match = re.search(r"\bPartitionCount:\s*(\d+)\b", result.stdout)
    if partition_count_match is not None:
        partition_count = int(partition_count_match.group(1))
    else:
        partitions = {
            int(match.group(1))
            for match in re.finditer(r"\bPartition:\s*(\d+)\b", result.stdout)
        }
        partition_count = len(partitions)

    if partition_count != EXPECTED_PARTITIONS:
        raise RuntimeError(
            f"Topic {topic_name} has {partition_count} partitions; expected {EXPECTED_PARTITIONS}"
        )
    return f"partitions={partition_count}"
