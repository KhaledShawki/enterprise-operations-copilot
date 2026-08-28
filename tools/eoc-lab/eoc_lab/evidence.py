from __future__ import annotations

from dataclasses import asdict, dataclass
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import platform
import secrets
import shutil
import tempfile
from typing import Any

from eoc_lab.process import CommandRunner


SCHEMA_VERSION = 1


def utc_now() -> datetime:
    return datetime.now(timezone.utc)


def isoformat_utc(value: datetime) -> str:
    return value.astimezone(timezone.utc).isoformat(timespec="milliseconds").replace("+00:00", "Z")


@dataclass(frozen=True)
class CheckResult:
    name: str
    passed: bool
    detail: str


@dataclass(frozen=True)
class RepositoryState:
    commit: str
    branch: str | None
    dirty: bool
    tracked_diff_sha256: str
    untracked_files: tuple[str, ...]
    untracked_content_sha256: str | None


class EvidenceStore:
    """Finalize complete evidence runs atomically within one filesystem."""

    def __init__(self, output_root: Path) -> None:
        self._output_root = output_root

    def write_run(self, run_id: str, manifest: dict[str, Any], result: dict[str, Any]) -> Path:
        self._validate_run_id(run_id)
        self._output_root.mkdir(parents=True, exist_ok=True)
        final_directory = self._output_root / run_id
        if final_directory.exists():
            raise FileExistsError(f"Evidence run already exists: {final_directory}")

        partial_directory = self._output_root / f".{run_id}.partial-{secrets.token_hex(4)}"
        partial_directory.mkdir(exist_ok=False)
        try:
            manifest_path = partial_directory / "manifest.json"
            result_path = partial_directory / "result.json"
            self._atomic_json_write(manifest_path, manifest)
            self._atomic_json_write(result_path, result)

            checksums = {
                "manifest.json": self.sha256_file(manifest_path),
                "result.json": self.sha256_file(result_path),
            }
            checksum_lines = [
                f"{digest}  {name}" for name, digest in sorted(checksums.items())
            ]
            self._atomic_text_write(
                partial_directory / "checksums.sha256", "\n".join(checksum_lines) + "\n"
            )
            self._fsync_directory(partial_directory)

            # os.rename does not replace an existing non-empty directory and remains atomic when
            # source and target are on the same filesystem, which they are by construction here.
            os.rename(partial_directory, final_directory)
            self._fsync_directory(self._output_root)
            return final_directory
        finally:
            if partial_directory.exists():
                shutil.rmtree(partial_directory)

    @staticmethod
    def _validate_run_id(run_id: str) -> None:
        if not run_id or run_id in {".", ".."} or "/" in run_id or "\\" in run_id:
            raise ValueError(f"Invalid evidence run id: {run_id!r}")

    @staticmethod
    def _atomic_json_write(path: Path, value: dict[str, Any]) -> None:
        serialized = json.dumps(value, indent=2, sort_keys=True, ensure_ascii=False) + "\n"
        EvidenceStore._atomic_text_write(path, serialized)

    @staticmethod
    def _atomic_text_write(path: Path, value: str) -> None:
        path.parent.mkdir(parents=True, exist_ok=True)
        file_descriptor, temporary_name = tempfile.mkstemp(prefix=f".{path.name}.", dir=path.parent)
        temporary_path = Path(temporary_name)
        try:
            with os.fdopen(file_descriptor, "w", encoding="utf-8", newline="\n") as stream:
                stream.write(value)
                stream.flush()
                os.fsync(stream.fileno())
            os.replace(temporary_path, path)
        finally:
            temporary_path.unlink(missing_ok=True)

    @staticmethod
    def _fsync_directory(path: Path) -> None:
        if os.name == "nt":
            return
        descriptor = os.open(path, os.O_RDONLY)
        try:
            os.fsync(descriptor)
        finally:
            os.close(descriptor)

    @staticmethod
    def sha256_file(path: Path) -> str:
        digest = hashlib.sha256()
        with path.open("rb") as stream:
            for chunk in iter(lambda: stream.read(1024 * 1024), b""):
                digest.update(chunk)
        return digest.hexdigest()


def repository_state(repo_root: Path, command_runner: CommandRunner) -> RepositoryState:
    commit_result = command_runner.run(["git", "-C", str(repo_root), "rev-parse", "HEAD"])
    if commit_result.returncode != 0 or not commit_result.stdout:
        raise RuntimeError(f"Cannot resolve git commit: {commit_result.stderr or commit_result.stdout}")

    branch_result = command_runner.run(
        ["git", "-C", str(repo_root), "symbolic-ref", "--quiet", "--short", "HEAD"]
    )
    branch = branch_result.stdout if branch_result.returncode == 0 and branch_result.stdout else None

    status_result = command_runner.run(
        ["git", "-C", str(repo_root), "status", "--porcelain", "--untracked-files=all"]
    )
    if status_result.returncode != 0:
        raise RuntimeError(f"Cannot resolve git status: {status_result.stderr or status_result.stdout}")

    diff_result = command_runner.run(["git", "-C", str(repo_root), "diff", "--binary", "HEAD", "--"])
    if diff_result.returncode != 0:
        raise RuntimeError(f"Cannot fingerprint tracked git changes: {diff_result.stderr}")

    untracked_result = command_runner.run(
        ["git", "-C", str(repo_root), "ls-files", "--others", "--exclude-standard"]
    )
    if untracked_result.returncode != 0:
        raise RuntimeError(f"Cannot list untracked git files: {untracked_result.stderr}")
    untracked_files = tuple(sorted(line for line in untracked_result.stdout.splitlines() if line))

    return RepositoryState(
        commit=commit_result.stdout,
        branch=branch,
        dirty=bool(status_result.stdout),
        tracked_diff_sha256=_sha256_text(diff_result.stdout),
        untracked_files=untracked_files,
        untracked_content_sha256=_untracked_content_fingerprint(repo_root, untracked_files),
    )


def environment_metadata() -> dict[str, Any]:
    return {
        "python": platform.python_version(),
        "implementation": platform.python_implementation(),
        "os": platform.platform(),
        "machine": platform.machine(),
        "processor": platform.processor() or None,
        "cpuCount": os.cpu_count(),
    }


def checks_as_dict(checks: list[CheckResult] | tuple[CheckResult, ...]) -> list[dict[str, Any]]:
    return [asdict(check) for check in checks]


def _sha256_text(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def _untracked_content_fingerprint(repo_root: Path, paths: tuple[str, ...]) -> str | None:
    if not paths:
        return None

    digest = hashlib.sha256()
    for relative_path in paths:
        path = repo_root / relative_path
        digest.update(relative_path.encode("utf-8"))
        digest.update(b"\0")
        if path.is_symlink():
            digest.update(os.readlink(path).encode("utf-8"))
        elif path.is_file():
            with path.open("rb") as stream:
                for chunk in iter(lambda: stream.read(1024 * 1024), b""):
                    digest.update(chunk)
        digest.update(b"\0")
    return digest.hexdigest()
