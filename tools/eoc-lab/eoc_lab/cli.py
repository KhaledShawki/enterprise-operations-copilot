from __future__ import annotations

import argparse
from pathlib import Path
import sys

from eoc_lab.baseline import execute_baseline
from eoc_lab.config import (
    ConfigurationError,
    DEFAULT_EXPECTED_ISSUER,
    DEFAULT_KEYCLOAK_BASE_URL,
    LabConfig,
)
from eoc_lab.doctor import DoctorReport, run_doctor
from eoc_lab.smoke import execute_smoke


MINIMUM_PYTHON = (3, 12)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="eoc-lab",
        description="External evidence and reliability lab for Enterprise Operations Copilot.",
    )
    parser.add_argument(
        "--env-file",
        type=Path,
        default=Path("deployment/compose/.env"),
        help="Compose environment file relative to the repository root.",
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=Path("evidence/runs"),
        help="Generated evidence directory relative to the repository root.",
    )
    parser.add_argument("--platform-url", help="Override the local platform base URL.")
    parser.add_argument(
        "--keycloak-url",
        default=DEFAULT_KEYCLOAK_BASE_URL,
        help="Network address used by the lab to reach Keycloak.",
    )
    parser.add_argument(
        "--expected-issuer",
        default=DEFAULT_EXPECTED_ISSUER,
        help="Exact OIDC issuer identity expected from discovery and JWT-backed API responses.",
    )

    subcommands = parser.add_subparsers(dest="command", required=True)
    subcommands.add_parser("doctor", help="Validate the local lab environment without mutating data.")

    run_parser = subcommands.add_parser("run", help="Execute an evidence scenario.")
    run_subcommands = run_parser.add_subparsers(dest="scenario", required=True)
    run_subcommands.add_parser("smoke", help="Create and read back one tenant through public APIs.")
    baseline_parser = run_subcommands.add_parser(
        "baseline",
        help="Run a deterministic external-source workload and reconcile Operations with Analytics.",
    )
    baseline_parser.add_argument(
        "--records", type=int, required=True, help="Deterministic invoice record count."
    )
    baseline_parser.add_argument(
        "--seed", type=int, default=42, help="Deterministic workload seed (default: 42)."
    )
    return parser


def main(argv: list[str] | None = None) -> int:
    if sys.version_info < MINIMUM_PYTHON:
        print(
            f"eoc-lab requires Python {MINIMUM_PYTHON[0]}.{MINIMUM_PYTHON[1]} or newer; "
            f"found {sys.version.split()[0]}",
            file=sys.stderr,
        )
        return 2

    parser = build_parser()
    arguments = parser.parse_args(argv)
    repo_root = _repo_root()

    try:
        config = LabConfig.load(
            repo_root=repo_root,
            env_file=arguments.env_file,
            output_root=arguments.output_dir,
            platform_base_url=arguments.platform_url,
            keycloak_base_url=arguments.keycloak_url,
            expected_issuer=arguments.expected_issuer,
        )
        if arguments.command == "doctor":
            report = run_doctor(config)
            _print_doctor(report)
            return 0 if report.passed else 1

        if arguments.command == "run" and arguments.scenario == "smoke":
            run = execute_smoke(config)
            print(f"runId: {run.run_id}")
            print(f"result: {run.status}")
            print(f"evidence: {run.run_directory}")
            return 0 if run.passed else 1

        if arguments.command == "run" and arguments.scenario == "baseline":
            run = execute_baseline(config, records=arguments.records, seed=arguments.seed)
            print(f"runId: {run.run_id}")
            print(f"result: {run.status}")
            print(f"evidence: {run.run_directory}")
            return 0 if run.passed else 1
    except (ConfigurationError, RuntimeError, OSError, ValueError) as exception:
        print(f"eoc-lab: {exception}", file=sys.stderr)
        return 1

    parser.error("Unsupported command")
    return 2


def _repo_root() -> Path:
    return Path(__file__).resolve().parents[3]


def _print_doctor(report: DoctorReport) -> None:
    for check in report.checks:
        marker = "PASS" if check.passed else "FAIL"
        print(f"[{marker}] {check.name}: {check.detail}")
    print(f"\nDoctor: {'PASS' if report.passed else 'FAIL'}")
