from pathlib import Path
import tempfile
import unittest

from eoc_lab.config import ConfigurationError, LabConfig, parse_env_file


REQUIRED_ENV = """\
PLATFORM_HTTP_PORT=8080
EOC_LAB_CLIENT_SECRET=local-secret-value
EOC_CONNECTOR_EVENTS_KAFKA_TOPIC=connector
EOC_CONNECTOR_EVENTS_KAFKA_DLT_TOPIC=connector.dlt
EOC_OPERATIONS_EVENTS_KAFKA_TOPIC=operations
EOC_ANALYTICS_EVENTS_KAFKA_DLT_TOPIC=analytics.dlt
"""


class ParseEnvFileTest(unittest.TestCase):
    def test_parses_comments_empty_values_and_equals_inside_values(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            path = Path(temporary_directory) / ".env"
            path.write_text(
                "# comment\nA=one\nB=two=three\nEMPTY=\nexport C=four\n",
                encoding="utf-8",
            )

            self.assertEqual(
                {"A": "one", "B": "two=three", "EMPTY": "", "C": "four"},
                parse_env_file(path),
            )

    def test_rejects_malformed_assignment(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            path = Path(temporary_directory) / ".env"
            path.write_text("NOT_AN_ASSIGNMENT\n", encoding="utf-8")

            with self.assertRaises(ConfigurationError):
                parse_env_file(path)


class LabConfigTest(unittest.TestCase):
    def test_requires_lab_secret_and_topics(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            (root / "deployment/compose").mkdir(parents=True)
            (root / "deployment/compose/compose.yaml").write_text("services: {}\n", encoding="utf-8")
            env_file = root / "deployment/compose/.env"
            env_file.write_text("PLATFORM_HTTP_PORT=8080\n", encoding="utf-8")

            with self.assertRaisesRegex(ConfigurationError, "EOC_LAB_CLIENT_SECRET"):
                LabConfig.load(
                    repo_root=root,
                    env_file=Path("deployment/compose/.env"),
                    output_root=Path("evidence/runs"),
                )

    def test_rejects_example_lab_secret(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            (root / "deployment/compose").mkdir(parents=True)
            (root / "deployment/compose/compose.yaml").write_text("services: {}\n", encoding="utf-8")
            env_file = root / "deployment/compose/.env"
            env_file.write_text(
                REQUIRED_ENV.replace("local-secret-value", "change-me-local-eoc-lab-client"),
                encoding="utf-8",
            )

            with self.assertRaisesRegex(ConfigurationError, "example value"):
                LabConfig.load(
                    repo_root=root,
                    env_file=Path("deployment/compose/.env"),
                    output_root=Path("evidence/runs"),
                )

    def test_keeps_network_address_separate_from_expected_issuer(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            (root / "deployment/compose").mkdir(parents=True)
            (root / "deployment/compose/compose.yaml").write_text("services: {}\n", encoding="utf-8")
            env_file = root / "deployment/compose/.env"
            env_file.write_text(REQUIRED_ENV, encoding="utf-8")

            config = LabConfig.load(
                repo_root=root,
                env_file=Path("deployment/compose/.env"),
                output_root=Path("evidence/runs"),
                keycloak_base_url="http://127.0.0.1:8180",
                expected_issuer="http://localhost:8180/realms/eoc",
            )

            self.assertEqual("http://127.0.0.1:8180", config.keycloak_base_url)
            self.assertEqual("http://localhost:8180/realms/eoc", config.expected_issuer)


if __name__ == "__main__":
    unittest.main()
