from __future__ import annotations

from dataclasses import asdict, dataclass
from datetime import date, datetime, time, timedelta, timezone
from decimal import Decimal
import hashlib
import json


SPEC_VERSION = "eoc-deterministic-workload-v1"
MAX_INVOICE_COUNT = 100_000
MAX_SEED = 2_147_483_647


@dataclass(frozen=True)
class Customer:
    sequence: int
    source_id: str
    source_version: str
    source_modified_at: str
    customer_number: str
    display_name: str
    email_address: str


@dataclass(frozen=True)
class Invoice:
    sequence: int
    source_id: str
    source_version: str
    source_modified_at: str
    customer_source_id: str
    invoice_number: str
    issue_date: str
    due_date: str
    currency: str
    total_amount: str
    open_amount: str
    source_status: str


@dataclass(frozen=True)
class Payment:
    sequence: int
    source_id: str
    source_version: str
    source_modified_at: str
    customer_source_id: str
    payment_date: str
    currency: str
    amount: str
    reversed: bool


@dataclass(frozen=True)
class Dataset:
    seed: int
    customers: tuple[Customer, ...]
    invoices: tuple[Invoice, ...]
    payments: tuple[Payment, ...]
    sha256: str


def build_dataset(seed: int, invoice_count: int) -> Dataset:
    _validate(seed, invoice_count)
    customer_count = min(25, max(1, invoice_count))
    customers = tuple(_customer(seed, index) for index in range(1, customer_count + 1))
    invoices = tuple(
        _invoice(seed, index, customer_count) for index in range(1, invoice_count + 1)
    )
    payments = tuple(
        payment
        for sequence, invoice in enumerate(invoices, start=1)
        if (payment := _payment(sequence, invoice)) is not None
    )
    digest = hashlib.sha256(
        json.dumps(
            {
                "specVersion": SPEC_VERSION,
                "seed": seed,
                "customers": [asdict(record) for record in customers],
                "invoices": [asdict(record) for record in invoices],
                "payments": [asdict(record) for record in payments],
            },
            sort_keys=True,
            separators=(",", ":"),
        ).encode("utf-8")
    ).hexdigest()
    return Dataset(seed, customers, invoices, payments, digest)


def _validate(seed: int, invoice_count: int) -> None:
    if seed < 0 or seed > MAX_SEED:
        raise ValueError(f"seed must be between 0 and {MAX_SEED}")
    if invoice_count < 1 or invoice_count > MAX_INVOICE_COUNT:
        raise ValueError(f"invoice_count must be between 1 and {MAX_INVOICE_COUNT}")


def _customer(seed: int, index: int) -> Customer:
    source_id = f"customer-{seed:010d}-{index:04d}"
    modified = datetime(2026, 1, 1, 9, tzinfo=timezone.utc) + timedelta(seconds=index)
    return Customer(
        sequence=index,
        source_id=source_id,
        source_version="v1",
        source_modified_at=_instant(modified),
        customer_number=f"C{seed % 10_000:04d}-{index:04d}",
        display_name=f"Deterministic Customer {index:04d}",
        email_address=f"customer-{index:04d}@example.invalid",
    )


def _invoice(seed: int, index: int, customer_count: int) -> Invoice:
    digest = hashlib.sha256(
        f"{SPEC_VERSION}|{seed}|invoice|{index}".encode("utf-8")
    ).digest()
    customer_index = int.from_bytes(digest[0:4], "big") % customer_count + 1
    total_cents = 10_000 + int.from_bytes(digest[4:8], "big") % 490_001
    state = digest[8] % 3
    if state == 0:
        open_cents = total_cents
        status = "OPEN"
    elif state == 1:
        open_cents = max(1, total_cents // 2)
        status = "PARTIALLY_PAID"
    else:
        open_cents = 0
        status = "PAID"
    issue_date = date(2026, 1, 1) + timedelta(days=digest[9] % 120)
    due_date = issue_date + timedelta(days=30)
    modified = datetime.combine(issue_date, time(12, 0), tzinfo=timezone.utc) + timedelta(
        seconds=index
    )
    return Invoice(
        sequence=index,
        source_id=f"invoice-{seed:010d}-{index:06d}",
        source_version="v1",
        source_modified_at=_instant(modified),
        customer_source_id=f"customer-{seed:010d}-{customer_index:04d}",
        invoice_number=f"INV-{seed % 10_000:04d}-{index:06d}",
        issue_date=issue_date.isoformat(),
        due_date=due_date.isoformat(),
        currency="USD",
        total_amount=_money(total_cents),
        open_amount=_money(open_cents),
        source_status=status,
    )


def _payment(sequence: int, invoice: Invoice) -> Payment | None:
    paid = _cents(invoice.total_amount) - _cents(invoice.open_amount)
    if paid == 0:
        return None
    payment_date = date.fromisoformat(invoice.issue_date) + timedelta(days=10)
    modified = datetime.combine(payment_date, time(15, 0), tzinfo=timezone.utc) + timedelta(
        seconds=sequence
    )
    return Payment(
        sequence=sequence,
        source_id=f"payment-{invoice.source_id}",
        source_version="v1",
        source_modified_at=_instant(modified),
        customer_source_id=invoice.customer_source_id,
        payment_date=payment_date.isoformat(),
        currency=invoice.currency,
        amount=_money(paid),
        reversed=False,
    )


def _money(cents: int) -> str:
    return f"{Decimal(cents) / Decimal(100):.2f}"


def _cents(value: str) -> int:
    return int(Decimal(value) * 100)


def _instant(value: datetime) -> str:
    return value.astimezone(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")
