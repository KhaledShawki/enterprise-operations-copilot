from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class WorkloadIdentity:
    access_token: str
    issuer: str
    subject: str
    roles: tuple[str, ...]


class ScenarioError(RuntimeError):
    def __init__(self, phase: str, code: str, message: str) -> None:
        super().__init__(message)
        self.phase = phase
        self.code = code


class AnalyticsDidNotConverge(RuntimeError):
    pass
