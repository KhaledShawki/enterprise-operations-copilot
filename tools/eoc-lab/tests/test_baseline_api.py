from __future__ import annotations

import json
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch
from urllib.parse import parse_qs, urlparse

import eoc_lab.baseline_api as baseline_api
from eoc_lab.baseline_types import AnalyticsDidNotConverge, ScenarioError, WorkloadIdentity
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
        token_provider = Mock(side_effect=["token-1", "token-1", "token-2", "token-2"])

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
                token_provider,
                TENANT_ID,
                expected_count=137,
            )

        self.assertIs(final_snapshot, actual)
        self.assertEqual(3, read_total.call_count)
        self.assertEqual(
            ["token-1", "token-1", "token-2"],
            [call.args[2] for call in read_total.call_args_list],
        )
        read_all.assert_called_once_with(_lab_config(), unittest.mock.ANY, "token-2", TENANT_ID)
        self.assertEqual(4, token_provider.call_count)
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
                side_effect=[0.0, 0.0, 2.0],
            ),
            patch.object(baseline_api.time, "sleep") as sleep,
        ):
            with self.assertRaisesRegex(
                AnalyticsDidNotConverge,
                "Analytics did not converge to 137 receivables within 1 seconds; last count was 81",
            ):
                baseline_api.wait_for_analytics(
                    _lab_config(),
                    Mock(),
                    lambda: "workload-token",
                    TENANT_ID,
                    expected_count=137,
                    timeout_seconds=1.0,
                )

        read_total.assert_called_once()
        read_all.assert_not_called()
        sleep.assert_called_once_with(baseline_api.POLL_INTERVAL_SECONDS)

    def test_wait_for_analytics_rejects_non_positive_timeout(self) -> None:
        with self.assertRaisesRegex(ValueError, "must be greater than zero"):
            baseline_api.wait_for_analytics(
                _lab_config(),
                Mock(),
                lambda: "workload-token",
                TENANT_ID,
                expected_count=137,
                timeout_seconds=0.0,
            )


class WorkloadTokenProviderTest(unittest.TestCase):
    def test_authentication_schedules_refresh_before_access_token_expiry(self) -> None:
        http = Mock()
        http.request.side_effect = [
            HttpResponse(
                status=200,
                headers={"content-type": "application/json"},
                body=json.dumps(
                    {"access_token": "token-1", "expires_in": 300}
                ).encode("utf-8"),
            ),
            HttpResponse(
                status=200,
                headers={"content-type": "application/json"},
                body=json.dumps(
                    {
                        "issuer": "http://issuer",
                        "subject": "workload-subject",
                        "roles": [],
                    }
                ).encode("utf-8"),
            ),
        ]

        with patch.object(baseline_api.time, "monotonic", return_value=100.0):
            identity = baseline_api.authenticate_workload_identity(_baseline_config(), http)

        self.assertEqual("token-1", identity.access_token)
        self.assertEqual(370.0, identity.refresh_at_monotonic)

    def test_provider_reauthenticates_same_service_account_before_expiry(self) -> None:
        initial = WorkloadIdentity(
            "token-1", "http://issuer", "workload-subject", (), refresh_at_monotonic=100.0
        )
        refreshed = WorkloadIdentity(
            "token-2", "http://issuer", "workload-subject", (), refresh_at_monotonic=400.0
        )
        provider = baseline_api.WorkloadTokenProvider(_baseline_config(), Mock(), initial)

        with (
            patch.object(baseline_api.time, "monotonic", side_effect=[99.0, 100.0]),
            patch.object(
                baseline_api, "authenticate_workload_identity", return_value=refreshed
            ) as authenticate,
        ):
            self.assertEqual("token-1", provider.access_token())
            self.assertEqual("token-2", provider.access_token())

        authenticate.assert_called_once()

    def test_provider_rejects_principal_change_during_reauthentication(self) -> None:
        initial = WorkloadIdentity(
            "token-1", "http://issuer", "workload-subject", (), refresh_at_monotonic=100.0
        )
        changed = WorkloadIdentity(
            "token-2", "http://issuer", "different-subject", (), refresh_at_monotonic=400.0
        )
        provider = baseline_api.WorkloadTokenProvider(_baseline_config(), Mock(), initial)

        with (
            patch.object(baseline_api.time, "monotonic", return_value=100.0),
            patch.object(
                baseline_api, "authenticate_workload_identity", return_value=changed
            ),
        ):
            with self.assertRaisesRegex(ScenarioError, "identity changed"):
                provider.access_token()


def _baseline_config():
    return SimpleNamespace(
        lab=SimpleNamespace(
            token_url="http://keycloak.test/realms/eoc/protocol/openid-connect/token",
            platform_base_url="http://platform.test",
            expected_issuer="http://issuer",
        ),
        workload_client_id="eoc-lab-workload",
        workload_client_secret="workload-secret",
    )


def _lab_config():
    return SimpleNamespace(platform_base_url="http://platform.test")


if __name__ == "__main__":
    unittest.main()
