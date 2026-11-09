"""独立临时用户的禁用与在线状态探针；不修改业务账号。"""
from pathlib import Path
import importlib.util
import json
import secrets
import sys
import unittest
import uuid

sys.dont_write_bytecode = True


def load(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


fixture = load("fixture", "Prepare-PocRealm.py")
protocol = load("protocol", "Test-PocIdentity.py")


class DisabledUserTest(unittest.TestCase):
    def test_disabled_user_refresh_and_introspection(self):
        config = json.loads(protocol.CONFIG.read_text(encoding="utf-8"))
        admin, headers = fixture.admin_session()
        realm = "http://localhost:18090/admin/realms/agent-doc-workbench-p0"
        representation = fixture.require(admin.get(realm, headers=headers, timeout=15), 200).json()
        self.assertEqual(representation.get("attributes", {}).get("adw.p0.fixture"), "v1")
        # 26.6.2起introspection客户端必须处于Access的aud；临时受众客户端仅供此测试。
        clients = fixture.require(admin.get(realm + "/clients", headers=headers,
                                            params={"clientId": "workbench-api"}, timeout=15), 200).json()
        self.assertEqual(len(clients), 0, "audience fixture client already exists; do not replace it")
        introspection_secret = secrets.token_urlsafe(32)
        created_client = fixture.require(admin.post(realm + "/clients", headers=headers, json={
            "clientId": "workbench-api", "enabled": True, "protocol": "openid-connect", "publicClient": False,
            "clientAuthenticatorType": "client-secret", "secret": introspection_secret,
            "standardFlowEnabled": False, "directAccessGrantsEnabled": False, "serviceAccountsEnabled": False,
            "attributes": {"adw.p0.fixture": "disabled-v1"},
        }, timeout=15), 201)
        client_id = created_client.headers["Location"].rsplit("/", 1)[1]
        self.addCleanup(lambda: fixture.require(admin.delete(realm + "/clients/" + client_id,
                                                             headers=headers, timeout=15), 204))
        username = "p0-disabled-" + uuid.uuid4().hex
        password = secrets.token_urlsafe(32)
        created = fixture.require(admin.post(realm + "/users", headers=headers, json={
            "username": username, "enabled": True, "email": username + "@example.invalid", "emailVerified": True,
            "firstName": "P0", "lastName": "Disabled", "attributes": {"adw.p0.fixture": ["disabled-v1"]},
            "credentials": [{"type": "password", "value": password, "temporary": False}],
        }, timeout=15), 201)
        user_id = created.headers["Location"].rsplit("/", 1)[1]
        self.addCleanup(lambda: fixture.require(admin.delete(realm + "/users/" + user_id,
                                                             headers=headers, timeout=15), 204))
        tester = protocol.KeycloakPocTest(methodName="test_authorization_code_cannot_be_reused")
        tester.config = {**config, "username": username, "password": password}
        tester.http = admin
        tester.discovery = fixture.require(admin.get(config["issuer"] + "/.well-known/openid-configuration", timeout=15), 200).json()
        tester.keys = fixture.require(admin.get(tester.discovery["jwks_uri"], timeout=15), 200).json()["keys"]
        code, verifier, _ = tester.authorization_code()
        tokens = fixture.require(tester.exchange(code, verifier), 200).json()
        auth = (config["webClientId"], config["webClientSecret"])
        introspect = tester.discovery["introspection_endpoint"]
        introspection_auth = ("workbench-api", introspection_secret)
        before = fixture.require(admin.post(introspect, auth=introspection_auth, data={"token": tokens["access_token"]}, timeout=15), 200).json()
        self.assertTrue(before["active"])
        fixture.require(admin.put(realm + "/users/" + user_id, headers=headers, json={"enabled": False}, timeout=15), 204)
        after = fixture.require(admin.post(introspect, auth=introspection_auth, data={"token": tokens["access_token"]}, timeout=15), 200).json()
        refreshed = admin.post(tester.discovery["token_endpoint"], auth=auth,
                               data={"grant_type": "refresh_token", "refresh_token": tokens["refresh_token"]}, timeout=15)
        self.assertEqual(refreshed.status_code, 400)
        tester.verify(tokens["access_token"], "workbench-api")
        print("introspection_active_after_disable=" + str(bool(after["active"])).lower())


if __name__ == "__main__":
    unittest.main(verbosity=2)
