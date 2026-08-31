from decimal import Decimal
import unittest

from eoc_lab.baseline_dataset import expected_dataset
from eoc_lab.reconciliation import (
    ReconciliationError,
    expected_business_facts,
    operations_business_facts,
)


class ReconciliationTest(unittest.TestCase):
    def test_expected_and_operations_business_facts_use_exact_money(self) -> None:
        expected = expected_business_facts(expected_dataset(42, 1))[0]
        operation = {
            "invoiceNumber": expected["invoiceNumber"],
            "originalAmount": {
                "amount": Decimal(expected["originalAmount"]["amount"]),
                "currency": "USD",
            },
            "paidAmount": {
                "amount": Decimal(expected["paidAmount"]["amount"]),
                "currency": "USD",
            },
            "openAmount": {
                "amount": Decimal(expected["openAmount"]["amount"]),
                "currency": "USD",
            },
            "issueDate": expected["issueDate"],
            "dueDate": expected["dueDate"],
            "status": expected["status"],
            "cancelled": False,
        }

        self.assertEqual([expected], operations_business_facts([operation]))

    def test_binary_float_money_is_rejected(self) -> None:
        expected = expected_business_facts(expected_dataset(42, 1))[0]
        operation = {
            "invoiceNumber": expected["invoiceNumber"],
            "originalAmount": {"amount": 10.5, "currency": "USD"},
            "paidAmount": {"amount": Decimal("0.00"), "currency": "USD"},
            "openAmount": {"amount": Decimal("10.50"), "currency": "USD"},
            "issueDate": expected["issueDate"],
            "dueDate": expected["dueDate"],
            "status": "OPEN",
            "cancelled": False,
        }

        with self.assertRaises(ReconciliationError):
            operations_business_facts([operation])

    def test_sub_cent_money_is_rejected_instead_of_rounded(self) -> None:
        expected = expected_business_facts(expected_dataset(42, 1))[0]
        operation = {
            "invoiceNumber": expected["invoiceNumber"],
            "originalAmount": {"amount": Decimal("10.001"), "currency": "USD"},
            "paidAmount": {"amount": Decimal("0.00"), "currency": "USD"},
            "openAmount": {"amount": Decimal("10.00"), "currency": "USD"},
            "issueDate": expected["issueDate"],
            "dueDate": expected["dueDate"],
            "status": "OPEN",
            "cancelled": False,
        }

        with self.assertRaises(ReconciliationError):
            operations_business_facts([operation])


if __name__ == "__main__":
    unittest.main()
