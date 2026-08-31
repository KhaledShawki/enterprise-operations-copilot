from __future__ import annotations

from dataclasses import dataclass
import unittest

from mock_erp.pagination import (
    InvalidSourcePosition,
    decode_cursor,
    decode_page_token,
    page_records,
)


@dataclass(frozen=True)
class Record:
    sequence: int
    value: str


class PaginationTest(unittest.TestCase):
    def setUp(self) -> None:
        self.records = tuple(Record(index, f"r-{index}") for index in range(1, 6))

    def test_pages_are_source_owned_and_continuable(self) -> None:
        first = page_records(
            "invoice",
            self.records,
            page_size=2,
            page_token="",
            incremental_cursor="",
        )
        second = page_records(
            "invoice",
            self.records,
            page_size=2,
            page_token=first.next_page_token,
            incremental_cursor="",
        )

        self.assertEqual(["r-1", "r-2"], [record.value for record in first.records])
        self.assertEqual(["r-3", "r-4"], [record.value for record in second.records])
        self.assertEqual((0, 2), decode_page_token(first.next_page_token, "invoice"))
        self.assertEqual("v1:invoice:2", first.candidate_cursor)

    def test_incremental_cursor_resumes_after_last_accepted_record(self) -> None:
        page = page_records(
            "customer",
            self.records,
            page_size=10,
            page_token="",
            incremental_cursor="v1:customer:2",
        )

        self.assertEqual(["r-3", "r-4", "r-5"], [record.value for record in page.records])
        self.assertEqual(5, decode_cursor(page.candidate_cursor, "customer"))
        self.assertEqual("", page.next_page_token)

    def test_entity_mismatch_and_combined_positions_are_rejected(self) -> None:
        first = page_records(
            "invoice",
            self.records,
            page_size=2,
            page_token="",
            incremental_cursor="",
        )

        with self.assertRaises(InvalidSourcePosition):
            decode_page_token(first.next_page_token, "customer")
        with self.assertRaises(InvalidSourcePosition):
            page_records(
                "invoice",
                self.records,
                page_size=2,
                page_token=first.next_page_token,
                incremental_cursor="v1:invoice:1",
            )

    def test_boolean_or_malformed_position_values_are_rejected(self) -> None:
        with self.assertRaises(InvalidSourcePosition):
            decode_page_token("eyJhIjp0cnVlLCJlIjoiaW52b2ljZSIsIm8iOjAsInYiOjF9", "invoice")
        with self.assertRaises(InvalidSourcePosition):
            decode_cursor("v1:invoice:not-a-number", "invoice")


if __name__ == "__main__":
    unittest.main()
