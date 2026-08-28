from __future__ import annotations

from dataclasses import dataclass
from typing import Any

from eoc_lab.config import LabConfig
from eoc_lab.http import HttpClient


@dataclass(frozen=True)
class LabIdentity:
    access_token: str
    issuer: str
    subject: str
    roles: tuple[str, ...]


def authenticate_lab_identity(config: LabConfig, http_client: HttpClient) -> LabIdentity:
    token_response = http_client.request(
        "POST",
        config.token_url,
        form_body={
            "grant_type": "client_credentials",
            "client_id": config.lab_client_id,
            "client_secret": config.lab_client_secret,
        },
    )
    if token_response.status != 200:
        raise RuntimeError(f"Lab service-account token request failed with HTTP {token_response.status}")

    token_payload: Any = token_response.json()
    if not isinstance(token_payload, dict):
        raise RuntimeError("Lab service-account token response is not a JSON object")
    access_token = token_payload.get("access_token")
    if not isinstance(access_token, str) or not access_token:
        raise RuntimeError("Lab service-account token response does not contain access_token")

    current_user_response = http_client.request(
        "GET",
        f"{config.platform_base_url}/api/v1/me",
        headers={"Authorization": f"Bearer {access_token}"},
    )
    if current_user_response.status != 200:
        raise RuntimeError(
            f"Authenticated current-user request failed with HTTP {current_user_response.status}"
        )
    if current_user_response.content_type != "application/json":
        raise RuntimeError(
            "Authenticated current-user response has unexpected content type: "
            f"{current_user_response.content_type!r}"
        )

    current_user: Any = current_user_response.json()
    if not isinstance(current_user, dict):
        raise RuntimeError("Current-user response is not a JSON object")
    issuer = current_user.get("issuer")
    subject = current_user.get("subject")
    roles = current_user.get("roles")
    if issuer != config.expected_issuer:
        raise RuntimeError(f"Current-user issuer does not match expected issuer: {issuer!r}")
    if not isinstance(subject, str) or not subject:
        raise RuntimeError("Current-user response does not contain a subject")
    if not isinstance(roles, list) or not all(isinstance(role, str) for role in roles):
        raise RuntimeError("Current-user response contains invalid roles")
    if "platform-admin" not in roles:
        raise RuntimeError("Lab service account does not have the platform-admin role")

    return LabIdentity(
        access_token=access_token,
        issuer=issuer,
        subject=subject,
        roles=tuple(sorted(roles)),
    )
