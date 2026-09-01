from __future__ import annotations

from datetime import date
from decimal import Decimal
import json
import time
from typing import Any, Callable
from urllib.parse import urlencode
from uuid import UUID, uuid4

from eoc_lab.baseline_config import BaselineConfig
from eoc_lab.baseline_types import AnalyticsDidNotConverge, ScenarioError, WorkloadIdentity
from eoc_lab.config import LabConfig
from eoc_lab.http import HttpClient, HttpResponse


BUSINESS_DATE = date(2026, 5, 15)
PAGE_SIZE = 100
CONVERGENCE_TIMEOUT_SECONDS = 300.0
POLL_INTERVAL_SECONDS = 0.5
TOKEN_REFRESH_FRACTION = 0.10
TOKEN_REFRESH_MARGIN_CAP_SECONDS = 30.0


def authenticate_workload_identity(config: BaselineConfig, http: HttpClient) -> WorkloadIdentity:
    response = http.request(
        "POST",
        config.lab.token_url,
        form_body={
            "grant_type": "client_credentials",
            "client_id": config.workload_client_id,
            "client_secret": config.workload_client_secret,
        },
    )
    if response.status != 200:
        raise ScenarioError(
            "authentication",
            "WORKLOAD_TOKEN_FAILED",
            f"Workload token request returned HTTP {response.status}",
        )
    payload = response_json(response, "workload token")
    token = payload.get("access_token")
    if not isinstance(token, str) or not token:
        raise ScenarioError(
            "authentication", "WORKLOAD_TOKEN_INVALID", "Workload token response has no token"
        )
    expires_in = payload.get("expires_in")
    if isinstance(expires_in, bool) or not isinstance(expires_in, (int, float)) or expires_in <= 0:
        raise ScenarioError(
            "authentication",
            "WORKLOAD_TOKEN_INVALID",
            "Workload token response has no positive expires_in",
        )
    issued_at = time.monotonic()
    refresh_margin = min(
        TOKEN_REFRESH_MARGIN_CAP_SECONDS, float(expires_in) * TOKEN_REFRESH_FRACTION
    )
    refresh_at = issued_at + float(expires_in) - refresh_margin

    current = http.request(
        "GET",
        f"{config.lab.platform_base_url}/api/v1/me",
        headers=authorization(token),
    )
    if current.status != 200:
        raise ScenarioError(
            "authentication",
            "WORKLOAD_IDENTITY_FAILED",
            f"Workload identity returned HTTP {current.status}",
        )
    identity = response_json(current, "workload identity")
    issuer = identity.get("issuer")
    subject = identity.get("subject")
    roles = identity.get("roles")
    if issuer != config.lab.expected_issuer or not isinstance(subject, str) or not subject:
        raise ScenarioError(
            "authentication", "WORKLOAD_IDENTITY_INVALID", "Workload identity is invalid"
        )
    if not isinstance(roles, list) or not all(isinstance(role, str) for role in roles):
        raise ScenarioError(
            "authentication", "WORKLOAD_IDENTITY_INVALID", "Workload roles are invalid"
        )
    return WorkloadIdentity(
        token, issuer, subject, tuple(sorted(roles)), refresh_at_monotonic=refresh_at
    )


class WorkloadTokenProvider:
    def __init__(
        self, config: BaselineConfig, http: HttpClient, identity: WorkloadIdentity
    ) -> None:
        self._config = config
        self._http = http
        self._identity = identity
        self._expected_principal = (identity.issuer, identity.subject, identity.roles)

    def access_token(self) -> str:
        refresh_at = self._identity.refresh_at_monotonic
        if refresh_at is not None and time.monotonic() >= refresh_at:
            refreshed = authenticate_workload_identity(self._config, self._http)
            actual_principal = (refreshed.issuer, refreshed.subject, refreshed.roles)
            if actual_principal != self._expected_principal:
                raise ScenarioError(
                    "authentication",
                    "WORKLOAD_IDENTITY_CHANGED",
                    "Workload service-account identity changed while refreshing its access token",
                )
            self._identity = refreshed
        return self._identity.access_token


def provision_workload_user(config: LabConfig, http: HttpClient, identity: WorkloadIdentity) -> str:
    response = http.request(
        "PUT",
        f"{config.platform_base_url}/api/v1/platform-users/me",
        headers=authorization(identity.access_token),
    )
    if response.status not in {200, 201}:
        raise ScenarioError(
            "workload-user-provisioning",
            "WORKLOAD_USER_PROVISION_FAILED",
            f"Platform user provisioning returned HTTP {response.status}",
        )
    payload = response_json(response, "platform user")
    return required_uuid(payload.get("id"), "platform user id")


def create_tenant(config: LabConfig, http: HttpClient, token: str, run_id: str) -> tuple[str, str]:
    tenant_key = f"baseline-{run_id[-12:]}".lower()
    response = http.request(
        "POST",
        f"{config.platform_base_url}/api/v1/tenants",
        headers=authorization(token),
        json_body={"tenantKey": tenant_key, "displayName": f"EOC Baseline {run_id}"},
    )
    if response.status != 201:
        raise ScenarioError(
            "tenant-create", "TENANT_CREATE_FAILED", f"Tenant creation returned HTTP {response.status}"
        )
    payload = response_json(response, "tenant")
    return required_uuid(payload.get("id"), "tenant id"), tenant_key


def assign_workload_membership(
    config: LabConfig,
    http: HttpClient,
    control_token: str,
    tenant_id: str,
    platform_user_id: str,
) -> None:
    base = f"{config.platform_base_url}/api/v1/tenants/{tenant_id}/memberships"
    assigned = http.request(
        "POST",
        base,
        headers=authorization(control_token),
        json_body={"platformUserId": platform_user_id},
    )
    if assigned.status != 201:
        raise ScenarioError(
            "tenant-membership",
            "MEMBERSHIP_ASSIGN_FAILED",
            f"Membership assignment returned HTTP {assigned.status}",
        )
    membership = response_json(assigned, "tenant membership")
    membership_id = required_uuid(membership.get("id"), "membership id")
    replaced = http.request(
        "PUT",
        f"{base}/{membership_id}/roles",
        headers=authorization(control_token),
        json_body={"roles": ["tenant-admin"]},
    )
    if replaced.status != 200:
        raise ScenarioError(
            "tenant-membership",
            "MEMBERSHIP_ROLE_ASSIGN_FAILED",
            f"Membership role assignment returned HTTP {replaced.status}",
        )
    payload = response_json(replaced, "tenant membership roles")
    roles = payload.get("roles")
    if not isinstance(roles, list) or set(roles) != {"tenant-admin"}:
        raise ScenarioError(
            "tenant-membership",
            "MEMBERSHIP_ROLE_MISMATCH",
            "Workload membership does not contain exactly tenant-admin",
        )


def create_and_activate_connector(
    config: LabConfig, http: HttpClient, token: str, tenant_id: str
) -> str:
    base = f"{config.platform_base_url}/api/v1/tenants/{tenant_id}/connectors"
    response = http.request(
        "POST",
        base,
        headers=authorization(token),
        json_body={
            "name": "Evidence Mock ERP",
            "type": "mock-erp",
            "endpoint": "grpc://mock-erp:9090",
            "credentialReference": str(uuid4()),
            "syncPolicy": {"mode": "MANUAL", "interval": "PT0S"},
        },
    )
    if response.status != 201:
        raise ScenarioError(
            "connector-create",
            "CONNECTOR_CREATE_FAILED",
            f"Connector creation returned HTTP {response.status}",
        )
    connector = response_json(response, "connector")
    connector_id = required_uuid(connector.get("id"), "connector id")
    activated = http.request("POST", f"{base}/{connector_id}/activation", headers=authorization(token))
    if activated.status != 200:
        raise ScenarioError(
            "connector-create",
            "CONNECTOR_ACTIVATION_FAILED",
            f"Connector activation returned HTTP {activated.status}",
        )
    activated_payload = response_json(activated, "activated connector")
    if activated_payload.get("status") != "ACTIVE":
        raise ScenarioError(
            "connector-create", "CONNECTOR_NOT_ACTIVE", "Connector did not become ACTIVE"
        )
    return connector_id


def request_and_execute_import(
    config: LabConfig,
    http: HttpClient,
    token: str,
    tenant_id: str,
    connector_id: str,
    import_type: str,
) -> dict[str, Any]:
    requested = http.request(
        "POST",
        f"{config.platform_base_url}/api/v1/tenants/{tenant_id}/connectors/{connector_id}/import-runs",
        headers=authorization(token),
        json_body={"importType": import_type, "mode": "FULL"},
    )
    if requested.status != 201:
        raise ScenarioError(
            "import", "IMPORT_REQUEST_FAILED", f"Import request returned HTTP {requested.status}"
        )
    run = response_json(requested, "import run")
    run_id = required_uuid(run.get("id"), "import run id")
    executed = http.request(
        "POST",
        f"{config.platform_base_url}/api/v1/tenants/{tenant_id}/import-runs/{run_id}/execution",
        headers=authorization(token),
        json_body={"pageSize": PAGE_SIZE},
    )
    if executed.status != 200:
        raise ScenarioError(
            "import", "IMPORT_EXECUTION_FAILED", f"Import execution returned HTTP {executed.status}"
        )
    return response_json(executed, "executed import run")


def import_statistics(run: dict[str, Any]) -> dict[str, int]:
    statistics = run.get("statistics")
    if not isinstance(statistics, dict):
        raise ScenarioError("import", "IMPORT_STATISTICS_INVALID", "Import statistics are missing")
    result: dict[str, int] = {}
    for key in ("fetched", "accepted", "rejected", "duplicates"):
        value = statistics.get(key)
        if isinstance(value, bool) or not isinstance(value, int) or value < 0:
            raise ScenarioError(
                "import", "IMPORT_STATISTICS_INVALID", f"Import statistic {key} is invalid"
            )
        result[key] = value
    return result


def read_all_invoices(
    config: LabConfig, http: HttpClient, token: str, tenant_id: str
) -> list[dict[str, Any]]:
    return _read_pages(
        config,
        http,
        token,
        f"/api/v1/tenants/{tenant_id}/invoices",
        "invoices",
        expected_business_date=BUSINESS_DATE.isoformat(),
    )


def read_all_receivables(
    config: LabConfig, http: HttpClient, token: str, tenant_id: str
) -> list[dict[str, Any]]:
    return _read_pages(
        config,
        http,
        token,
        f"/api/v1/tenants/{tenant_id}/analytics/receivables",
        "receivables",
        expected_business_date=BUSINESS_DATE.isoformat(),
    )


def wait_for_analytics(
    config: LabConfig,
    http: HttpClient,
    token_provider: Callable[[], str],
    tenant_id: str,
    *,
    expected_count: int,
    timeout_seconds: float = CONVERGENCE_TIMEOUT_SECONDS,
) -> list[dict[str, Any]]:
    if timeout_seconds <= 0:
        raise ValueError("Analytics convergence timeout must be greater than zero")
    deadline = time.monotonic() + timeout_seconds
    last_count = -1
    while time.monotonic() < deadline:
        last_count = _read_receivable_total(config, http, token_provider(), tenant_id)
        if last_count == expected_count:
            return read_all_receivables(config, http, token_provider(), tenant_id)
        time.sleep(POLL_INTERVAL_SECONDS)
    raise AnalyticsDidNotConverge(
        f"Analytics did not converge to {expected_count} receivables within "
        f"{timeout_seconds:g} seconds; last count was {last_count}"
    )


def _read_receivable_total(
    config: LabConfig, http: HttpClient, token: str, tenant_id: str
) -> int:
    query = urlencode(
        {
            "businessDate": BUSINESS_DATE.isoformat(),
            "page": 0,
            "size": 1,
            "sort": "INVOICE_NUMBER",
            "direction": "ASC",
        }
    )
    response = http.request(
        "GET",
        f"{config.platform_base_url}/api/v1/tenants/{tenant_id}/analytics/receivables?{query}",
        headers=authorization(token),
    )
    if response.status != 200:
        raise ScenarioError(
            "analytics-convergence",
            "ANALYTICS_COUNT_READ_FAILED",
            f"Analytics receivable count returned HTTP {response.status}",
        )
    payload = response_json(response, "analytics receivable count")
    if payload.get("businessDate") != BUSINESS_DATE.isoformat():
        raise ScenarioError(
            "analytics-convergence",
            "BUSINESS_DATE_MISMATCH",
            "Analytics receivable count used an unexpected business date",
        )
    total_elements = payload.get("totalElements")
    if isinstance(total_elements, bool) or not isinstance(total_elements, int) or total_elements < 0:
        raise ScenarioError(
            "analytics-convergence",
            "INVALID_PAGE_METADATA",
            "Analytics receivable totalElements is invalid",
        )
    return total_elements


def read_summary(config: LabConfig, http: HttpClient, token: str, tenant_id: str) -> dict[str, Any]:
    query = urlencode({"businessDate": BUSINESS_DATE.isoformat()})
    response = http.request(
        "GET",
        f"{config.platform_base_url}/api/v1/tenants/{tenant_id}/analytics/receivables/summary?{query}",
        headers=authorization(token),
    )
    if response.status != 200:
        raise ScenarioError(
            "analytics-reconciliation",
            "SUMMARY_READ_FAILED",
            f"Analytics summary returned HTTP {response.status}",
        )
    return response_json(response, "analytics summary")


def _read_pages(
    config: LabConfig,
    http: HttpClient,
    token: str,
    path: str,
    content_key: str,
    *,
    expected_business_date: str,
) -> list[dict[str, Any]]:
    items: list[dict[str, Any]] = []
    page = 0
    total_elements: int | None = None
    while True:
        query = urlencode(
            {
                "businessDate": expected_business_date,
                "page": page,
                "size": PAGE_SIZE,
                "sort": "INVOICE_NUMBER",
                "direction": "ASC",
            }
        )
        response = http.request(
            "GET", f"{config.platform_base_url}{path}?{query}", headers=authorization(token)
        )
        if response.status != 200:
            raise ScenarioError(
                "reconciliation", "READ_API_FAILED", f"{content_key} read returned HTTP {response.status}"
            )
        payload = response_json(response, content_key)
        if payload.get("businessDate") != expected_business_date:
            raise ScenarioError(
                "reconciliation",
                "BUSINESS_DATE_MISMATCH",
                f"{content_key} response used an unexpected business date",
            )
        page_items = payload.get(content_key)
        if not isinstance(page_items, list) or not all(isinstance(item, dict) for item in page_items):
            raise ScenarioError(
                "reconciliation", "INVALID_PAGE_CONTENT", f"{content_key} page content is invalid"
            )
        current_total = payload.get("totalElements")
        if isinstance(current_total, bool) or not isinstance(current_total, int) or current_total < 0:
            raise ScenarioError("reconciliation", "INVALID_PAGE_METADATA", "totalElements is invalid")
        if total_elements is None:
            total_elements = current_total
        elif total_elements != current_total:
            raise ScenarioError(
                "reconciliation", "UNSTABLE_PAGE_METADATA", "totalElements changed while paging"
            )
        items.extend(page_items)
        has_next = payload.get("hasNext")
        if not isinstance(has_next, bool):
            raise ScenarioError("reconciliation", "INVALID_PAGE_METADATA", "hasNext is not boolean")
        if not has_next:
            break
        page += 1
        if page > 10_000:
            raise ScenarioError(
                "reconciliation", "PAGE_LIMIT_EXCEEDED", "Paging exceeded the safety limit"
            )
    if total_elements != len(items):
        raise ScenarioError(
            "reconciliation", "PAGE_TOTAL_MISMATCH", "Paged content does not match totalElements"
        )
    return items


def response_json(response: HttpResponse, label: str) -> dict[str, Any]:
    try:
        payload = json.loads(response.body.decode("utf-8"), parse_float=Decimal)
    except (UnicodeDecodeError, json.JSONDecodeError) as exception:
        raise ScenarioError("protocol", "INVALID_JSON", f"{label} is not valid JSON") from exception
    if not isinstance(payload, dict):
        raise ScenarioError("protocol", "INVALID_JSON_SHAPE", f"{label} is not a JSON object")
    return payload


def authorization(token: str) -> dict[str, str]:
    return {"Authorization": f"Bearer {token}"}


def required_uuid(value: Any, label: str) -> str:
    if not isinstance(value, str):
        raise ScenarioError("protocol", "INVALID_UUID", f"{label} is missing")
    try:
        return str(UUID(value))
    except ValueError as exception:
        raise ScenarioError("protocol", "INVALID_UUID", f"{label} is not a UUID") from exception
