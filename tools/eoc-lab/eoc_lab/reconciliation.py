from __future__ import annotations

from datetime import date
from decimal import Decimal, InvalidOperation
import hashlib
import json
from typing import Any, Iterable
from uuid import UUID

from eoc_lab.baseline_dataset import ExpectedDataset


CENT = Decimal("0.01")


class ReconciliationError(ValueError):
    pass


def expected_business_facts(dataset: ExpectedDataset) -> list[dict[str, Any]]:
    facts: list[dict[str, Any]] = []
    for invoice in dataset.invoices:
        total = _money(invoice.total_amount)
        open_amount = _money(invoice.open_amount)
        facts.append(
            {
                "invoiceNumber": invoice.invoice_number,
                "originalAmount": _money_object(total, invoice.currency),
                "paidAmount": _money_object(total - open_amount, invoice.currency),
                "openAmount": _money_object(open_amount, invoice.currency),
                "issueDate": invoice.issue_date,
                "dueDate": invoice.due_date,
                "status": invoice.source_status,
                "cancelled": False,
            }
        )
    return sorted(facts, key=lambda item: item["invoiceNumber"])


def operations_business_facts(invoices: Iterable[dict[str, Any]]) -> list[dict[str, Any]]:
    return sorted(
        [_operations_business_fact(invoice) for invoice in invoices],
        key=lambda item: item["invoiceNumber"],
    )


def operations_projection_facts(invoices: Iterable[dict[str, Any]]) -> list[dict[str, Any]]:
    facts = []
    for invoice in invoices:
        business = _operations_business_fact(invoice)
        facts.append(
            {
                "id": _uuid(invoice.get("id"), "Operations invoice id"),
                "customerId": _uuid(invoice.get("customerId"), "Operations customer id"),
                **business,
            }
        )
    return sorted(facts, key=lambda item: item["invoiceNumber"])


def analytics_projection_facts(receivables: Iterable[dict[str, Any]]) -> list[dict[str, Any]]:
    facts = []
    for receivable in receivables:
        customer = _object(receivable.get("customer"), "Analytics customer")
        facts.append(
            {
                "id": _uuid(receivable.get("id"), "Analytics invoice id"),
                "customerId": _uuid(customer.get("id"), "Analytics customer id"),
                "invoiceNumber": _text(receivable.get("invoiceNumber"), "Analytics invoice number"),
                "originalAmount": _api_money(receivable.get("originalAmount"), "originalAmount"),
                "paidAmount": _api_money(receivable.get("paidAmount"), "paidAmount"),
                "openAmount": _api_money(
                    receivable.get("outstandingAmount"), "outstandingAmount"
                ),
                "issueDate": _date_text(receivable.get("issueDate"), "Analytics issue date"),
                "dueDate": _date_text(receivable.get("dueDate"), "Analytics due date"),
                "status": _text(receivable.get("status"), "Analytics status"),
                "cancelled": _boolean(receivable.get("cancelled"), "Analytics cancelled"),
            }
        )
    return sorted(facts, key=lambda item: item["invoiceNumber"])


def lineage_invariants(receivables: Iterable[dict[str, Any]]) -> dict[str, bool]:
    event_ids: list[str] = []
    versions: list[int] = []
    for receivable in receivables:
        source = _object(receivable.get("source"), "Analytics source lineage")
        event_ids.append(_uuid(source.get("eventId"), "Analytics source event id"))
        version = source.get("aggregateVersion")
        if isinstance(version, bool) or not isinstance(version, int) or version < 1:
            raise ReconciliationError("Analytics aggregateVersion must be a positive integer")
        versions.append(version)
        _text(source.get("occurredAt"), "Analytics source occurredAt")
    return {
        "analyticsEventIdsUnique": len(event_ids) == len(set(event_ids)),
        "analyticsFreshAggregateVersionsAreOne": all(version == 1 for version in versions),
    }


def canonical_hash(records: list[dict[str, Any]]) -> str:
    payload = json.dumps(records, sort_keys=True, separators=(",", ":"), ensure_ascii=False)
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()


def expected_summary(dataset: ExpectedDataset, business_date: date) -> dict[str, Any]:
    invoice_count = len(dataset.invoices)
    open_invoices = [invoice for invoice in dataset.invoices if _money(invoice.open_amount) > 0]
    overdue_invoices = [
        invoice
        for invoice in open_invoices
        if date.fromisoformat(invoice.due_date) < business_date
    ]
    outstanding = sum((_money(invoice.open_amount) for invoice in open_invoices), Decimal("0.00"))
    overdue = sum((_money(invoice.open_amount) for invoice in overdue_invoices), Decimal("0.00"))
    aging = {
        "currentAmount": Decimal("0.00"),
        "days1To30OverdueAmount": Decimal("0.00"),
        "days31To60OverdueAmount": Decimal("0.00"),
        "days61To90OverdueAmount": Decimal("0.00"),
        "days91PlusOverdueAmount": Decimal("0.00"),
    }
    for invoice in open_invoices:
        amount = _money(invoice.open_amount)
        days_overdue = (business_date - date.fromisoformat(invoice.due_date)).days
        if days_overdue <= 0:
            aging["currentAmount"] += amount
        elif days_overdue <= 30:
            aging["days1To30OverdueAmount"] += amount
        elif days_overdue <= 60:
            aging["days31To60OverdueAmount"] += amount
        elif days_overdue <= 90:
            aging["days61To90OverdueAmount"] += amount
        else:
            aging["days91PlusOverdueAmount"] += amount

    return {
        "invoiceCount": invoice_count,
        "openCount": len(open_invoices),
        "overdueCount": len(overdue_invoices),
        "currencies": [
            {
                "currency": "USD",
                "invoiceCount": invoice_count,
                "openCount": len(open_invoices),
                "overdueCount": len(overdue_invoices),
                "outstandingAmount": _money_string(outstanding),
                "overdueAmount": _money_string(overdue),
                "aging": {key: _money_string(value) for key, value in aging.items()},
            }
        ],
    }


def normalize_summary(payload: dict[str, Any], *, tenant_id: str, business_date: str) -> dict[str, Any]:
    if _uuid(payload.get("tenantId"), "Summary tenant id") != tenant_id:
        raise ReconciliationError("Analytics summary tenant id does not match the workload tenant")
    if _date_text(payload.get("businessDate"), "Summary business date") != business_date:
        raise ReconciliationError("Analytics summary business date does not match the requested date")
    currencies = payload.get("currencies")
    if not isinstance(currencies, list):
        raise ReconciliationError("Analytics summary currencies must be a list")
    normalized_currencies = []
    for value in currencies:
        currency = _object(value, "Analytics currency summary")
        aging = _object(currency.get("aging"), "Analytics aging summary")
        normalized_currencies.append(
            {
                "currency": _text(currency.get("currency"), "Summary currency"),
                "invoiceCount": _integer(currency.get("invoiceCount"), "Currency invoice count"),
                "openCount": _integer(currency.get("openCount"), "Currency open count"),
                "overdueCount": _integer(currency.get("overdueCount"), "Currency overdue count"),
                "outstandingAmount": _money_string(_money(currency.get("outstandingAmount"))),
                "overdueAmount": _money_string(_money(currency.get("overdueAmount"))),
                "aging": {
                    key: _money_string(_money(aging.get(key)))
                    for key in (
                        "currentAmount",
                        "days1To30OverdueAmount",
                        "days31To60OverdueAmount",
                        "days61To90OverdueAmount",
                        "days91PlusOverdueAmount",
                    )
                },
            }
        )
    normalized_currencies.sort(key=lambda item: item["currency"])
    return {
        "invoiceCount": _integer(payload.get("invoiceCount"), "Summary invoice count"),
        "openCount": _integer(payload.get("openCount"), "Summary open count"),
        "overdueCount": _integer(payload.get("overdueCount"), "Summary overdue count"),
        "currencies": normalized_currencies,
    }


def _operations_business_fact(invoice: dict[str, Any]) -> dict[str, Any]:
    return {
        "invoiceNumber": _text(invoice.get("invoiceNumber"), "Operations invoice number"),
        "originalAmount": _api_money(invoice.get("originalAmount"), "originalAmount"),
        "paidAmount": _api_money(invoice.get("paidAmount"), "paidAmount"),
        "openAmount": _api_money(invoice.get("openAmount"), "openAmount"),
        "issueDate": _date_text(invoice.get("issueDate"), "Operations issue date"),
        "dueDate": _date_text(invoice.get("dueDate"), "Operations due date"),
        "status": _text(invoice.get("status"), "Operations status"),
        "cancelled": _boolean(invoice.get("cancelled"), "Operations cancelled"),
    }


def _api_money(value: Any, label: str) -> dict[str, str]:
    money = _object(value, label)
    return _money_object(
        _money(money.get("amount")),
        _text(money.get("currency"), f"{label} currency"),
    )


def _money_object(amount: Decimal, currency: str) -> dict[str, str]:
    return {"amount": _money_string(amount), "currency": currency}


def _money(value: Any) -> Decimal:
    if isinstance(value, bool) or isinstance(value, float) or value is None:
        raise ReconciliationError("Money must be represented exactly, never as a binary float")
    try:
        decimal_value = value if isinstance(value, Decimal) else Decimal(str(value))
    except (InvalidOperation, ValueError) as exception:
        raise ReconciliationError(f"Invalid monetary value: {value!r}") from exception
    quantized = decimal_value.quantize(CENT)
    if decimal_value != quantized:
        raise ReconciliationError(f"Monetary value has unsupported sub-cent precision: {value!r}")
    return quantized


def _money_string(value: Decimal) -> str:
    return f"{value.quantize(CENT):.2f}"


def _object(value: Any, label: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise ReconciliationError(f"{label} must be a JSON object")
    return value


def _text(value: Any, label: str) -> str:
    if not isinstance(value, str) or not value:
        raise ReconciliationError(f"{label} must be a non-empty string")
    return value


def _date_text(value: Any, label: str) -> str:
    text = _text(value, label)
    try:
        date.fromisoformat(text)
    except ValueError as exception:
        raise ReconciliationError(f"{label} must be an ISO date") from exception
    return text


def _uuid(value: Any, label: str) -> str:
    text = _text(value, label)
    try:
        return str(UUID(text))
    except ValueError as exception:
        raise ReconciliationError(f"{label} must be a UUID") from exception


def _boolean(value: Any, label: str) -> bool:
    if not isinstance(value, bool):
        raise ReconciliationError(f"{label} must be a boolean")
    return value


def _integer(value: Any, label: str) -> int:
    if isinstance(value, bool) or not isinstance(value, int) or value < 0:
        raise ReconciliationError(f"{label} must be a non-negative integer")
    return value
