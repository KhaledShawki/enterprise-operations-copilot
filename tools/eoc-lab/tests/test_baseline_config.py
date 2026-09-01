from __future__ import annotations

from pathlib import Path
from types import SimpleNamespace
import tempfile
import unittest

from eoc_lab.baseline_config import BaselineConfig
from eoc_lab.config import ConfigurationError


class BaselineConfigTest(unittest.TestCase):
    def test_uses_local_prometheus_port_from_env(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            lab = _lab(Path(temporary_directory), prometheus_port="19090")

            config = BaselineConfig.from_lab(lab)

        self.assertEqual("http://127.0.0.1:19090", config.prometheus_base_url)
        self.assertIn("observability", config.observability_compose_prefix)

    def test_rejects_invalid_prometheus_port(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            lab = _lab(Path(temporary_directory), prometheus_port="not-a-port")

            with self.assertRaisesRegex(ConfigurationError, "PROMETHEUS_HTTP_PORT"):
                BaselineConfig.from_lab(lab)


def _lab(root: Path, *, prometheus_port: str) -> SimpleNamespace:
    compose_file = _write(root, "deployment/compose/compose.yaml", "services: {}\n")
    _write(root, "deployment/compose/compose.evidence.yaml", "services: {}\n")
    _write(root, "deployment/compose/compose.observability.yaml", "services: {}\n")
    env_file = _write(
        root,
        "deployment/compose/.env",
        "\n".join(
            (
                "EOC_LAB_WORKLOAD_CLIENT_SECRET=workload-secret",
                f"PROMETHEUS_HTTP_PORT={prometheus_port}",
                "",
            )
        ),
    )
    return SimpleNamespace(repo_root=root, env_file=env_file, compose_file=compose_file)


def _write(root: Path, relative: str, content: str) -> Path:
    path = root / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")
    return path


if __name__ == "__main__":
    unittest.main()
