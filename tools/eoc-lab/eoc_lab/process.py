from __future__ import annotations

from dataclasses import dataclass
import subprocess
from typing import Sequence


@dataclass(frozen=True)
class CommandResult:
    argv: tuple[str, ...]
    returncode: int
    stdout: str
    stderr: str


class CommandRunner:
    def run(self, argv: Sequence[str], *, timeout_seconds: float = 30.0) -> CommandResult:
        completed = subprocess.run(
            list(argv),
            capture_output=True,
            check=False,
            text=True,
            timeout=timeout_seconds,
        )
        return CommandResult(
            argv=tuple(argv),
            returncode=completed.returncode,
            stdout=completed.stdout.strip(),
            stderr=completed.stderr.strip(),
        )
