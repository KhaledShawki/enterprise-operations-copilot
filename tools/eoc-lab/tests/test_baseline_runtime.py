from __future__ import annotations

from pathlib import Path
from types import SimpleNamespace
import tempfile
import unittest
from unittest.mock import patch

from eoc_lab import baseline_runtime
from eoc_lab.process import CommandResult


class FakeRunner:
    def run(self, argv, *, timeout_seconds=30.0):
        command = tuple(str(value) for value in argv)
        if "ps" in command and "--quiet" in command:
            service = command[-1]
            return CommandResult(command, 0, f"container-{service}", "")
        if command[:2] == ("docker", "inspect"):
            service = command[-1].removeprefix("container-")
            return CommandResult(command, 0, f"image/{service}:local|sha256:{service}", "")
        if command[-2:] == ("config", "--quiet"):
            return CommandResult(command, 0, "", "")
        raise AssertionError(f"Unexpected command: {command}")


class BaselineRuntimeTest(unittest.TestCase):
    def test_observability_capture_extends_runtime_provenance(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            evidence_compose = _write(root, "deployment/compose/compose.evidence.yaml", "services: {}\n")
            observability_compose = _write(
                root, "deployment/compose/compose.observability.yaml", "services: {}\n"
            )
            _write(root, "deployment/observability/prometheus/prometheus.yml", "global: {}\n")
            _write(
                root,
                "deployment/observability/grafana/dashboards/eoc-event-pipeline.json",
                "{}\n",
            )
            config = SimpleNamespace(
                lab=SimpleNamespace(
                    repo_root=root,
                    lab_client_secret="control-secret",
                ),
                workload_client_secret="workload-secret",
                evidence_compose_file=evidence_compose,
                observability_compose_file=observability_compose,
                compose_prefix=["docker", "compose", "--file", "base", "--file", "evidence"],
                observability_compose_prefix=[
                    "docker",
                    "compose",
                    "--file",
                    "base",
                    "--file",
                    "evidence",
                    "--file",
                    "observability",
                    "--profile",
                    "evidence",
                    "--profile",
                    "observability",
                ],
            )
            base = {"captureStatus": "OK", "images": {}, "configuration": {}}

            with patch.object(
                baseline_runtime, "collect_runtime_provenance", return_value=base
            ):
                runtime = baseline_runtime.capture_runtime_provenance(
                    config, FakeRunner(), include_observability=True
                )

        self.assertEqual("OK", runtime["captureStatus"])
        self.assertEqual("sha256:mock-erp", runtime["images"]["mock-erp"]["imageId"])
        self.assertEqual("sha256:prometheus", runtime["images"]["prometheus"]["imageId"])
        self.assertEqual("sha256:grafana", runtime["images"]["grafana"]["imageId"])
        self.assertIn("observabilityComposeFileSha256", runtime["configuration"])
        self.assertIn("prometheusConfigSha256", runtime["configuration"])
        self.assertIn("grafanaDashboardSha256", runtime["configuration"])

    def test_observability_validation_uses_combined_profile_prefix(self) -> None:
        config = SimpleNamespace(
            compose_prefix=["docker", "compose", "--file", "base", "--file", "evidence"],
            observability_compose_prefix=[
                "docker",
                "compose",
                "--file",
                "base",
                "--file",
                "evidence",
                "--file",
                "observability",
                "--profile",
                "evidence",
                "--profile",
                "observability",
            ],
        )
        runner = FakeRunner()

        baseline_runtime.validate_evidence_compose(
            config, runner, include_observability=True
        )


def _write(root: Path, relative: str, content: str) -> Path:
    path = root / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")
    return path


if __name__ == "__main__":
    unittest.main()
