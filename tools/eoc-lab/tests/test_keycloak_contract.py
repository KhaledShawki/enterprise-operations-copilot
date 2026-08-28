import json
from pathlib import Path
import unittest


REPO_ROOT = Path(__file__).resolve().parents[3]
REALM_PATH = REPO_ROOT / "deployment/keycloak/eoc-realm.json"


class EvidenceLabKeycloakContractTest(unittest.TestCase):
    def test_lab_client_is_service_account_only_and_audienced_for_platform_api(self) -> None:
        realm = json.loads(REALM_PATH.read_text(encoding="utf-8"))
        client = next(client for client in realm["clients"] if client["clientId"] == "eoc-lab")

        self.assertTrue(client["enabled"])
        self.assertFalse(client["publicClient"])
        self.assertTrue(client["serviceAccountsEnabled"])
        self.assertFalse(client["standardFlowEnabled"])
        self.assertFalse(client["implicitFlowEnabled"])
        self.assertFalse(client["directAccessGrantsEnabled"])
        self.assertEqual("${EOC_LAB_CLIENT_SECRET}", client["secret"])

        audiences = [
            mapper["config"].get("included.client.audience")
            for mapper in client["protocolMappers"]
            if mapper.get("protocolMapper") == "oidc-audience-mapper"
        ]
        self.assertIn("platform-api", audiences)

    def test_lab_service_account_has_only_explicit_platform_admin_realm_role(self) -> None:
        realm = json.loads(REALM_PATH.read_text(encoding="utf-8"))
        user = next(
            user for user in realm["users"] if user.get("serviceAccountClientId") == "eoc-lab"
        )

        self.assertTrue(user["enabled"])
        self.assertEqual("service-account-eoc-lab", user["username"])
        self.assertEqual(["platform-admin"], user["realmRoles"])
        self.assertNotIn("credentials", user)


if __name__ == "__main__":
    unittest.main()
