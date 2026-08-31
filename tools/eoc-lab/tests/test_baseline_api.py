from __future__ import annotations

import json
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch
from urllib.parse import parse_qs, urlparse

import eoc_lab.baseline_api as baseline_api
from eoc_lab.baseline_types import AnalyticsDidNotConverge
from eoc_lab.http import HttpResponse


TENANT_ID = "00000000-0000-0000-0000-000000000101"


class AnalyticsConvergenceTest(unittest.TestCase):
    def test_receivable_total_reads_single_page_metadata_only(self) -> None:
        http = Mock()
        http.request.return_value = HttpResponse(
            status=200,
            headers={"content-type": "application/json"},
            body=json.dumps(
                {
                    "businessDate": baseline_api.BUSINESS_DATE.isoformat(),
                    "receivables": [],
                    "totalElements": 137,
                    "hasNext": True,
                }
            ).encode("utf-8"),
        )

        total = baseline_api._read_receivable_total(
            _lab_config(), http, "workload-token", TENANT_ID
        )

        self.assertEqual(137, total)
        http.request.assert_called_once()
        method, url = http.request.call_args.args
        self.assertEqual("GET", method)
        query = parse_qs(urlparse(url).query)
        self.assertEqual(["0"], query["page"])
        self.assertEqual(["1"], query["size"])
        self.assertEqual([baseline_api.BUSINESS_DATE.isoformat()], query["businessDate"])

    def test_wait_for_analytics_defers_full_snapshot_until_target_count(self) -> None:
        final_snapshot = [{"invoiceId": str(index)} for index in range(137)]

        with (
            patch.object(
                baseline_api,
                "_read_receivable_total",
                side_effect=[12, 81, 137],
            ) as read_total,
            patch.object(
                baseline_api,
                "read_all_receivables",
                return_value=final_snapshot,
            ) as read_all,
            patch.object(
                baseline_api.time,
                "monotonic",
                side_effect=[0.0, 0.0, 1.0, 2.0],
            ),
            patch.object(baseline_api.time, "sleep") as sleep,
        ):
            actual = baseline_api.wait_for_analytics(
                _lab_config(),
                Mock(),
                "workload-token",
                TENANT_ID,
                expected_count=137,
            )

        self.assertIs(final_snapshot, actual)
        self.assertEqual(3, read_total.call_count)
        read_all.assert_called_once()
        self.assertEqual(2, sleep.call_count)

    def test_non_convergence_is_bounded_without_full_snapshot_read(self) -> None:
        with (
            patch.object(
                baseline_api,
                "_read_receivable_total",
                return_value=81,
            ) as read_total,
            patch.object(baseline_api, "read_all_receivables") as read_all,
            patch.object(
                baseline_api.time,
                "monotonic",
                side_effect=[0.0, 0.0, baseline_api.CONVERGENCE_TIMEOUT_SECONDS + 1.0],
            ),
            patch.object(baseline_api.time, "sleep") as sleep,
        ):
            with self.assertRaisesRegex(
                AnalyticsDidNotConverge,
                "Analytics did not converge to 137 receivables; last count was 81",
            ):
                baseline_api.wait_for_analytics(
                    _lab_config(),
                    Mock(),
                    "workload-token",
                    TENANT_ID,
                    expected_count=137,
                )

        read_total.assert_called_once()
        read_all.assert_not_called()
        sleep.assert_called_once_with(baseline_api.POLL_INTERVAL_SECONDS)


def _lab_config():
    return SimpleNamespace(platform_base_url="http://platform.test")


if __name__ == "__main__":
    unittest.main()
