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
        return cls(lab, WORKLOAD_CLIENT_ID, secret, evidence_compose_file)

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
