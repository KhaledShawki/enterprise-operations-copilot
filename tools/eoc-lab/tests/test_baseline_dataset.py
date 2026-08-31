import unittest

from eoc_lab.baseline_dataset import MAX_RECORDS, MAX_SEED, expected_dataset


class BaselineDatasetTest(unittest.TestCase):
    def test_seed_42_golden_vector_is_stable(self) -> None:
        dataset = expected_dataset(42, 3)

        self.assertEqual(3, len(dataset.customers))
        self.assertEqual(3, len(dataset.invoices))
        self.assertEqual(
            "acf87787008cb3dd5aef6af7bc1419393dc9ff37c003957d8d6e66888f16e62f",
            dataset.sha256,
        )
        self.assertEqual("INV-0042-000001", dataset.invoices[0].invoice_number)
        self.assertEqual("USD", dataset.invoices[0].currency)

    def test_large_seed_and_multi_page_record_count_are_deterministic(self) -> None:
        first = expected_dataset(MAX_SEED, 137)
        second = expected_dataset(MAX_SEED, 137)

        self.assertEqual(first, second)
        self.assertEqual(25, len(first.customers))
        self.assertEqual(137, len(first.invoices))

    def test_invalid_seed_or_record_count_is_rejected(self) -> None:
        for seed, records in ((-1, 1), (MAX_SEED + 1, 1), (0, 0), (0, MAX_RECORDS + 1)):
            with self.subTest(seed=seed, records=records):
                with self.assertRaises(ValueError):
                    expected_dataset(seed, records)


if __name__ == "__main__":
    unittest.main()
