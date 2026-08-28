from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from urllib.parse import urlparse


class ConfigurationError(ValueError):
    """Raised when the lab configuration is missing or invalid."""


EXAMPLE_SECRET_PREFIX = "change-me-"
DEFAULT_KEYCLOAK_BASE_URL = "http://127.0.0.1:8180"
DEFAULT_EXPECTED_ISSUER = "http://localhost:8180/realms/eoc"


def parse_env_file(path: Path) -> dict[str, str]:
    if not path.is_file():
        raise ConfigurationError(f"Environment file does not exist: {path}")

    values: dict[str, str] = {}
    for line_number, raw_line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            raise ConfigurationError(f"Invalid environment assignment at {path}:{line_number}")

        key, value = line.split("=", 1)
        key = key.strip()
        if key.startswith("export "):
            key = key.removeprefix("export ").strip()
        if not key or any(character.isspace() for character in key):
            raise ConfigurationError(f"Invalid environment key at {path}:{line_number}: {key!r}")

        values[key] = value.strip()

    return values


def require(values: dict[str, str], key: str) -> str:
    value = values.get(key, "").strip()
    if not value:
        raise ConfigurationError(f"Required configuration value is missing: {key}")
    return value


def _base_url(value: str, field_name: str) -> str:
    normalized = value.rstrip("/")
    parsed = urlparse(normalized)
    if parsed.scheme not in {"http", "https"} or not parsed.netloc or parsed.query or parsed.fragment:
        raise ConfigurationError(f"{field_name} must be an absolute HTTP(S) URL")
    return normalized


def _issuer(value: str) -> str:
    normalized = value.rstrip("/")
    parsed = urlparse(normalized)
    if parsed.scheme not in {"http", "https"} or not parsed.netloc:
        raise ConfigurationError("Expected issuer must be an absolute HTTP(S) URL")
    return normalized


@dataclass(frozen=True)
class LabConfig:
    repo_root: Path
    env_file: Path
    output_root: Path
    platform_base_url: str
    keycloak_base_url: str
    expected_issuer: str
    lab_client_id: str
    lab_client_secret: str
    connector_topic: str
    connector_dlt_topic: str
    operations_topic: str
    analytics_dlt_topic: str
    compose_file: Path

    @classmethod
    def load(
        cls,
        *,
        repo_root: Path,
        env_file: Path,
        output_root: Path,
        platform_base_url: str | None = None,
        keycloak_base_url: str = DEFAULT_KEYCLOAK_BASE_URL,
        expected_issuer: str = DEFAULT_EXPECTED_ISSUER,
    ) -> "LabConfig":
        repo_root = repo_root.resolve()
        env_file = env_file if env_file.is_absolute() else repo_root / env_file
        output_root = output_root if output_root.is_absolute() else repo_root / output_root
        values = parse_env_file(env_file)

        platform_port = require(values, "PLATFORM_HTTP_PORT")
        resolved_platform_url = platform_base_url or f"http://127.0.0.1:{platform_port}"
        lab_secret = require(values, "EOC_LAB_CLIENT_SECRET")
        if lab_secret.startswith(EXAMPLE_SECRET_PREFIX):
            raise ConfigurationError(
                "EOC_LAB_CLIENT_SECRET still contains the example value; configure a local secret"
            )

        compose_file = repo_root / "deployment/compose/compose.yaml"
        if not compose_file.is_file():
            raise ConfigurationError(f"Compose file does not exist: {compose_file}")

        return cls(
            repo_root=repo_root,
            env_file=env_file.resolve(),
            output_root=output_root.resolve(),
            platform_base_url=_base_url(resolved_platform_url, "Platform URL"),
            keycloak_base_url=_base_url(keycloak_base_url, "Keycloak URL"),
            expected_issuer=_issuer(expected_issuer),
            lab_client_id="eoc-lab",
            lab_client_secret=lab_secret,
            connector_topic=require(values, "EOC_CONNECTOR_EVENTS_KAFKA_TOPIC"),
            connector_dlt_topic=require(values, "EOC_CONNECTOR_EVENTS_KAFKA_DLT_TOPIC"),
            operations_topic=require(values, "EOC_OPERATIONS_EVENTS_KAFKA_TOPIC"),
            analytics_dlt_topic=require(values, "EOC_ANALYTICS_EVENTS_KAFKA_DLT_TOPIC"),
            compose_file=compose_file,
        )

    @property
    def compose_prefix(self) -> list[str]:
        return [
            "docker",
            "compose",
            "--env-file",
            str(self.env_file),
            "--file",
            str(self.compose_file),
        ]

    @property
    def token_url(self) -> str:
        return f"{self.keycloak_base_url}/realms/eoc/protocol/openid-connect/token"
