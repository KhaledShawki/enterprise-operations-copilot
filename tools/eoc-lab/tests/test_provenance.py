from pathlib import Path
import tempfile
import unittest

from eoc_lab.config import LabConfig
from eoc_lab.process import CommandResult
from eoc_lab.provenance import RUNTIME_SERVICES, collect_runtime_provenance


class FakeCommandRunner:
    def run(self, argv, *, timeout_seconds=30.0):
        command = tuple(argv)
        if command[:2] == ("docker", "version"):
            return CommandResult(command, 0, "28.3.1", "")
        if command[:3] == ("docker", "compose", "version"):
            return CommandResult(command, 0, "2.39.1", "")
        if "ps" in command and "--quiet" in command:
            service = command[-1]
            return CommandResult(command, 0, f"container-{service}", "")
        if command[:2] == ("docker", "inspect"):
            service = command[-1].removeprefix("container-")
            return CommandResult(command, 0, f"image/{service}:local|sha256:{service}", "")
        raise AssertionError(f"Unexpected command: {command}")


class RuntimeProvenanceTest(unittest.TestCase):
    def test_records_image_identities_without_secrets(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            compose_file = root / "deployment/compose/compose.yaml"
            compose_file.parent.mkdir(parents=True)
            compose_file.write_text("services: {}\n", encoding="utf-8")
            env_file = root / "deployment/compose/.env"
            env_file.write_text("secret-does-not-belong-in-output\n", encoding="utf-8")
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

            provenance = collect_runtime_provenance(config, FakeCommandRunner())

            self.assertEqual("OK", provenance["captureStatus"])
            self.assertEqual(set(RUNTIME_SERVICES), set(provenance["images"]))
            self.assertNotIn("super-secret", str(provenance))
            self.assertNotIn("secret-does-not-belong-in-output", str(provenance))
            self.assertEqual("http://localhost:8180/realms/eoc", provenance["configuration"]["expectedIssuer"])


if __name__ == "__main__":
    unittest.main()
