from __future__ import annotations

import hashlib
import json
from pathlib import Path
from typing import Any

from eoc_lab.config import LabConfig
from eoc_lab.process import CommandRunner


RUNTIME_SERVICES = (
    "platform-postgres",
    "keycloak-postgres",
    "keycloak",
    "kafka",
    "platform-service",
    "web-bff",
)


def collect_runtime_provenance(config: LabConfig, command_runner: CommandRunner) -> dict[str, Any]:
    docker_version = _required_stdout(
        command_runner.run(["docker", "version", "--format", "{{.Server.Version}}"]),
        "Docker server version",
    )
    compose_version = _required_stdout(
        command_runner.run(["docker", "compose", "version", "--short"]),
        "Docker Compose version",
    )

    images: dict[str, dict[str, str]] = {}
    for service in RUNTIME_SERVICES:
        container_id = _required_stdout(
            command_runner.run([*config.compose_prefix, "ps", "--quiet", service]),
            f"container id for {service}",
        )
        inspected = _required_stdout(
            command_runner.run(
                [
                    "docker",
                    "inspect",
                    "--format",
                    "{{.Config.Image}}|{{.Image}}",
                    container_id,
                ]
            ),
            f"container image for {service}",
        )
        try:
            image_reference, image_id = inspected.split("|", 1)
        except ValueError as exception:
            raise RuntimeError(f"Unexpected Docker image metadata for {service}") from exception
        images[service] = {"reference": image_reference, "imageId": image_id}

    safe_configuration = {
        "composeFileSha256": _sha256_file(config.compose_file),
        "platformBaseUrl": config.platform_base_url,
        "keycloakBaseUrl": config.keycloak_base_url,
        "expectedIssuer": config.expected_issuer,
        "labClientId": config.lab_client_id,
        "kafkaTopics": [
            config.connector_topic,
            config.connector_dlt_topic,
            config.operations_topic,
            config.analytics_dlt_topic,
        ],
    }
    configuration_fingerprint = hashlib.sha256(
        json.dumps(safe_configuration, sort_keys=True, separators=(",", ":")).encode("utf-8")
    ).hexdigest()

    return {
        "captureStatus": "OK",
        "docker": {"serverVersion": docker_version, "composeVersion": compose_version},
        "images": images,
        "configuration": safe_configuration,
        "configurationSha256": configuration_fingerprint,
    }


def _required_stdout(result, label: str) -> str:
    if result.returncode != 0 or not result.stdout:
        raise RuntimeError(f"Cannot resolve {label}: {result.stderr or result.stdout}")
    return result.stdout


def _sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()
