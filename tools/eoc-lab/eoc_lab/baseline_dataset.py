from __future__ import annotations

from dataclasses import asdict, dataclass
from datetime import date, datetime, time, timedelta, timezone
from decimal import Decimal
import hashlib
import json


SPEC_VERSION = "eoc-deterministic-workload-v1"
MAX_RECORDS = 100_000
MAX_SEED = 2_147_483_647


@dataclass(frozen=True)
class ExpectedCustomer:
    sequence: int
    source_id: str
    source_version: str
    source_modified_at: str
    customer_number: str
    display_name: str
    email_address: str


@dataclass(frozen=True)
class ExpectedInvoice:
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
class ExpectedPayment:
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
class ExpectedDataset:
    seed: int
    customers: tuple[ExpectedCustomer, ...]
    invoices: tuple[ExpectedInvoice, ...]
    payments: tuple[ExpectedPayment, ...]
    sha256: str


def expected_dataset(seed: int, records: int) -> ExpectedDataset:
    if seed < 0 or seed > MAX_SEED:
        raise ValueError(f"seed must be between 0 and {MAX_SEED}")
    if records < 1 or records > MAX_RECORDS:
        raise ValueError(f"records must be between 1 and {MAX_RECORDS}")

    customer_count = min(25, max(1, records))
    customers = tuple(_customer(seed, index) for index in range(1, customer_count + 1))
    invoices = tuple(_invoice(seed, index, customer_count) for index in range(1, records + 1))
    payments = tuple(
        payment
        for sequence, invoice in enumerate(invoices, start=1)
        if (payment := _payment(sequence, invoice)) is not None
    )
    canonical = {
        "specVersion": SPEC_VERSION,
        "seed": seed,
        "customers": [asdict(item) for item in customers],
        "invoices": [asdict(item) for item in invoices],
        "payments": [asdict(item) for item in payments],
    }
    sha256 = hashlib.sha256(
        json.dumps(canonical, sort_keys=True, separators=(",", ":")).encode("utf-8")
    ).hexdigest()
    return ExpectedDataset(seed, customers, invoices, payments, sha256)


def _customer(seed: int, index: int) -> ExpectedCustomer:
    modified = datetime(2026, 1, 1, 9, tzinfo=timezone.utc) + timedelta(seconds=index)
    return ExpectedCustomer(
        sequence=index,
        source_id=f"customer-{seed:010d}-{index:04d}",
        source_version="v1",
        source_modified_at=_instant(modified),
        customer_number=f"C{seed % 10_000:04d}-{index:04d}",
        display_name=f"Deterministic Customer {index:04d}",
        email_address=f"customer-{index:04d}@example.invalid",
    )


def _invoice(seed: int, index: int, customer_count: int) -> ExpectedInvoice:
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
    issued = date(2026, 1, 1) + timedelta(days=digest[9] % 120)
    due = issued + timedelta(days=30)
    modified = datetime.combine(issued, time(12), tzinfo=timezone.utc) + timedelta(seconds=index)
    return ExpectedInvoice(
        sequence=index,
        source_id=f"invoice-{seed:010d}-{index:06d}",
        source_version="v1",
        source_modified_at=_instant(modified),
        customer_source_id=f"customer-{seed:010d}-{customer_index:04d}",
        invoice_number=f"INV-{seed % 10_000:04d}-{index:06d}",
        issue_date=issued.isoformat(),
        due_date=due.isoformat(),
        currency="USD",
        total_amount=_money(total_cents),
        open_amount=_money(open_cents),
        source_status=status,
    )


def _payment(sequence: int, invoice: ExpectedInvoice) -> ExpectedPayment | None:
    paid_cents = _cents(invoice.total_amount) - _cents(invoice.open_amount)
    if paid_cents == 0:
        return None
    paid_on = date.fromisoformat(invoice.issue_date) + timedelta(days=10)
    modified = datetime.combine(paid_on, time(15), tzinfo=timezone.utc) + timedelta(seconds=sequence)
    return ExpectedPayment(
        sequence=sequence,
        source_id=f"payment-{invoice.source_id}",
        source_version="v1",
        source_modified_at=_instant(modified),
        customer_source_id=invoice.customer_source_id,
        payment_date=paid_on.isoformat(),
        currency=invoice.currency,
        amount=_money(paid_cents),
        reversed=False,
    )


def _money(cents: int) -> str:
    return f"{Decimal(cents) / Decimal(100):.2f}"


def _cents(value: str) -> int:
    return int(Decimal(value) * 100)


def _instant(value: datetime) -> str:
    return value.astimezone(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")
