"""真实Keycloak密码策略/TOTP样本；只创建并删除独立测试realm内的临时用户。"""
from pathlib import Path
from urllib.parse import urlparse
import hashlib
import hmac
import importlib.util
import json
import secrets
import struct
import sys
import time
import unittest
import uuid


sys.dont_write_bytecode = True


def module(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    value = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(value)
    return value


protocol = module("poc_protocol", "Test-PocIdentity.py")
fixture = module("poc_fixture", "Prepare-PocRealm.py")


class KeycloakMfaPocTest(unittest.TestCase):
    def setUp(self):
        self.config = json.loads(protocol.CONFIG.read_text(encoding="utf-8"))
        self.http, self.headers = fixture.admin_session()
        self.realm = "http://localhost:18090/admin/realms/agent-doc-workbench-p0"
        realm = fixture.require(self.http.get(self.realm, headers=self.headers, timeout=15), 200).json()
        self.assertEqual(realm.get("attributes", {}).get("adw.p0.fixture"), "v1")
        self.assertEqual(self.config["issuer"], fixture.ISSUER)
        self.assertEqual((realm["otpPolicyType"], realm["otpPolicyAlgorithm"], realm["otpPolicyDigits"], realm["otpPolicyPeriod"]),
                         ("totp", "HmacSHA1", 6, 30))
        self.user_id = str(uuid.uuid4())
        self.username = "p0-mfa-" + uuid.uuid4().hex
        self.password = secrets.token_urlsafe(32)
        created = fixture.require(self.http.post(self.realm + "/users", headers=self.headers, json={
            "id": self.user_id, "username": self.username, "enabled": True,
            "firstName": "P0", "lastName": "MFA", "email": self.username + "@example.invalid", "emailVerified": True,
            "requiredActions": ["CONFIGURE_TOTP"],
            "attributes": {"adw.p0.fixture": ["mfa-v1"]},
            "credentials": [{"type": "password", "value": self.password, "temporary": False}],
        }, timeout=15), 201)
        # 创建接口可重新生成ID，以服务端Location为准；不按用户名批量删除。
        self.user_id = created.headers["Location"].rsplit("/", 1)[1]
        self.addCleanup(lambda: fixture.require(self.http.delete(self.realm + "/users/" + self.user_id,
                                                               headers=self.headers, timeout=15), 204))

    def test_password_policy_rejects_short_password(self):
        response = self.http.put(self.realm + "/users/" + self.user_id + "/reset-password", headers=self.headers,
                                 json={"type": "password", "value": "short", "temporary": False}, timeout=15)
        self.assertEqual(response.status_code, 400)
        fixture.require(self.http.put(self.realm + "/users/" + self.user_id + "/reset-password", headers=self.headers,
                                      json={"type": "password", "value": secrets.token_urlsafe(32), "temporary": False}, timeout=15), 204)

    def send(self, browser, method, url, **kwargs):
        self.assertEqual(urlparse(url).netloc, urlparse(self.config["issuer"]).netloc)
        return protocol.KeycloakPocTest.send(browser, method, url, allow_redirects=False, **kwargs)

    def password_login(self, browser):
        response = self.send(browser, "GET", self.config["issuer"] + "/protocol/openid-connect/auth", params={
            "client_id": self.config["webClientId"], "response_type": "code", "scope": "openid workbench:access",
            "redirect_uri": self.config["bffUrl"] + "/login/oauth2/code/workbench",
            "state": secrets.token_urlsafe(24), "nonce": secrets.token_urlsafe(24),
            "code_challenge_method": "S256", "code_challenge": secrets.token_urlsafe(32),
        })
        self.assertEqual(response.status_code, 200)
        form = self.form(response, "kc-form-login")
        response = self.send(browser, "POST", form.action, data={**form.hidden, "username": self.username, "password": self.password})
        if response.status_code == 302 and response.headers.get("Location", "").startswith(self.config["issuer"] + "/"):
            response = self.send(browser, "GET", response.headers["Location"])
        return response

    def form(self, response, form_id):
        form = protocol.LoginForm(form_id)
        form.feed(response.text)
        self.assertIsNotNone(form.action)
        return form

    @staticmethod
    def totp(secret, window=0):
        # Keycloak设置表单的totpSecret是原始字符串，认证器二维码使用它的Base32编码。
        digest = hmac.new(secret.encode(), struct.pack(">Q", int(time.time()) // 30 + window), hashlib.sha1).digest()
        offset = digest[-1] & 15
        return str((struct.unpack(">I", digest[offset:offset + 4])[0] & 0x7fffffff) % 1000000).zfill(6)

    def test_totp_enrollment_and_challenge_block_code_until_verified(self):
        browser = protocol.LocalhostBrowserSession()
        setup = self.password_login(browser)
        self.assertEqual(setup.status_code, 200)
        form = self.form(setup, "kc-totp-settings-form")
        self.assertNotIn("Location", setup.headers)
        secret = form.hidden["totpSecret"]
        enrolled = self.send(browser, "POST", form.action,
                             data={**form.hidden, "totp": self.totp(secret), "userLabel": "P0 temporary"})
        self.assertEqual(enrolled.status_code, 302)
        self.assertTrue(enrolled.headers["Location"].startswith(self.config["bffUrl"] + "/login/oauth2/code/workbench?"))
        browser = protocol.LocalhostBrowserSession()
        challenge = self.password_login(browser)
        self.assertEqual(challenge.status_code, 200)
        form = self.form(challenge, "kc-otp-login-form")
        self.assertNotIn("Location", challenge.headers)
        # 生成当前窗口及相邻窗口均不匹配的负例，避免固定000000偶然合法。
        valid = {self.totp(secret, window) for window in range(-2, 3)}
        wrong = next(str(value).zfill(6) for value in range(1000000) if str(value).zfill(6) not in valid)
        rejected = self.send(browser, "POST", form.action, data={**form.hidden, "otp": wrong})
        self.assertEqual(rejected.status_code, 200)
        form = self.form(rejected, "kc-otp-login-form")
        accepted = self.send(browser, "POST", form.action, data={**form.hidden, "otp": self.totp(secret)})
        self.assertEqual(accepted.status_code, 302)
        self.assertTrue(accepted.headers["Location"].startswith(self.config["bffUrl"] + "/login/oauth2/code/workbench?"))


if __name__ == "__main__":
    unittest.main(verbosity=2)
