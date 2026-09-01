from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

from eoc_lab.config import ConfigurationError, EXAMPLE_SECRET_PREFIX, LabConfig, parse_env_file, require


WORKLOAD_CLIENT_ID = "eoc-lab-workload"


@dataclass(frozen=True)
class BaselineConfig:
    lab: LabConfig
    workload_client_id: str
    workload_client_secret: str
    evidence_compose_file: Path
    observability_compose_file: Path
    prometheus_base_url: str

    @classmethod
    def from_lab(cls, lab: LabConfig) -> "BaselineConfig":
        values = parse_env_file(lab.env_file)
        secret = require(values, "EOC_LAB_WORKLOAD_CLIENT_SECRET")
        if secret.startswith(EXAMPLE_SECRET_PREFIX):
            raise ConfigurationError(
                "EOC_LAB_WORKLOAD_CLIENT_SECRET still contains the example value; "
                "configure a local secret before running baseline evidence"
            )
        evidence_compose_file = lab.repo_root / "deployment/compose/compose.evidence.yaml"
        if not evidence_compose_file.is_file():
            raise ConfigurationError(f"Evidence Compose file does not exist: {evidence_compose_file}")
        observability_compose_file = lab.repo_root / "deployment/compose/compose.observability.yaml"
        if not observability_compose_file.is_file():
            raise ConfigurationError(
                f"Observability Compose file does not exist: {observability_compose_file}"
            )
        prometheus_port = values.get("PROMETHEUS_HTTP_PORT", "9090").strip() or "9090"
        if not prometheus_port.isdigit() or not 1 <= int(prometheus_port) <= 65535:
            raise ConfigurationError("PROMETHEUS_HTTP_PORT must be an integer between 1 and 65535")
        return cls(
            lab,
            WORKLOAD_CLIENT_ID,
            secret,
            evidence_compose_file,
            observability_compose_file,
            f"http://127.0.0.1:{prometheus_port}",
        )

    @property
    def compose_prefix(self) -> list[str]:
        return [
            "docker",
            "compose",
            "--env-file",
            str(self.lab.env_file),
            "--file",
            str(self.lab.compose_file),
            "--file",
            str(self.evidence_compose_file),
        ]

    @property
    def observability_compose_prefix(self) -> list[str]:
        return [
            *self.compose_prefix,
            "--file",
            str(self.observability_compose_file),
            "--profile",
            "evidence",
            "--profile",
            "observability",
        ]
