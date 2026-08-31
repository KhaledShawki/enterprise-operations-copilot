from __future__ import annotations

import hashlib
import json
from typing import Any

from eoc_lab.baseline_config import BaselineConfig
from eoc_lab.baseline_dataset import SPEC_VERSION, ExpectedDataset
from eoc_lab.baseline_types import ScenarioError
from eoc_lab.process import CommandRunner
from eoc_lab.provenance import collect_runtime_provenance


def validate_evidence_compose(config: BaselineConfig, runner: CommandRunner) -> None:
    result = runner.run([*config.compose_prefix, "config", "--quiet"])
    if result.returncode != 0:
        raise ScenarioError(
            "preflight", "EVIDENCE_COMPOSE_INVALID", result.stderr or "Evidence Compose is invalid"
        )


def configure_mock_erp(
    config: BaselineConfig,
    runner: CommandRunner,
    expected: ExpectedDataset,
    records: int,
    seed: int,
) -> dict[str, Any]:
    result = runner.run(
        [
            *config.compose_prefix,
            "exec",
            "-T",
            "mock-erp",
            "python",
            "-m",
            "mock_erp.control_cli",
            "configure",
            "--seed",
            str(seed),
            "--records",
            str(records),
        ]
    )
    if result.returncode != 0:
        raise ScenarioError(
            "preflight",
            "MOCK_ERP_CONFIGURATION_FAILED",
            result.stderr or "Mock ERP configuration failed",
        )
    try:
        metadata = json.loads(result.stdout)
    except json.JSONDecodeError as exception:
        raise ScenarioError(
            "preflight", "MOCK_ERP_METADATA_INVALID", "Mock ERP returned invalid metadata"
        ) from exception
    expected_metadata = {
        "specVersion": SPEC_VERSION,
        "seed": seed,
        "invoiceCount": records,
        "customerCount": len(expected.customers),
        "datasetSha256": expected.sha256,
    }
    if metadata != expected_metadata:
        raise ScenarioError(
            "preflight",
            "MOCK_ERP_DATASET_MISMATCH",
            "Mock ERP dataset metadata does not match the independent expectation",
        )
    return metadata


def capture_runtime_provenance(config: BaselineConfig, runner: CommandRunner) -> dict[str, Any]:
    try:
        runtime = collect_runtime_provenance(config.lab, runner)
        container = runner.run([*config.compose_prefix, "ps", "--quiet", "mock-erp"])
        if container.returncode != 0 or not container.stdout:
            raise RuntimeError("Cannot resolve Mock ERP container id")
        inspected = runner.run(
            ["docker", "inspect", "--format", "{{.Config.Image}}|{{.Image}}", container.stdout]
        )
        if inspected.returncode != 0 or "|" not in inspected.stdout:
            raise RuntimeError("Cannot inspect Mock ERP image")
        reference, image_id = inspected.stdout.split("|", 1)
        runtime["images"]["mock-erp"] = {"reference": reference, "imageId": image_id}
        runtime["configuration"]["evidenceComposeFileSha256"] = sha256_file(
            config.evidence_compose_file
        )
        return runtime
    except Exception as exception:
        return {
            "captureStatus": "ERROR",
            "errorType": type(exception).__name__,
            "message": redact(str(exception) or type(exception).__name__, config),
        }


def redact(value: str, config: BaselineConfig) -> str:
    if not value:
        return value
    return value.replace(config.lab.lab_client_secret, "<redacted>").replace(
        config.workload_client_secret, "<redacted>"
    )


def sha256_file(path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()
