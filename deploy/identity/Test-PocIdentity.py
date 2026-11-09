"""真实Keycloak协议探针；只打印状态/声明摘要，凭据及Token仅在内存中使用。"""
from html.parser import HTMLParser
from concurrent.futures import ThreadPoolExecutor
from http.cookiejar import DefaultCookiePolicy
from pathlib import Path
from urllib.parse import urlencode, urlparse, parse_qs
import base64
import hashlib
import json
import secrets
import time
import threading
import unittest

from cryptography.hazmat.primitives.asymmetric import rsa, padding
from cryptography.hazmat.primitives import hashes
import requests


ROOT = Path(__file__).resolve().parents[2]
CONFIG = ROOT / ".codex-validation-store/v0.3.0-p0/runtime/oidc-poc.json"


def unbase64(value):
    return base64.urlsafe_b64decode(value + "=" * (-len(value) % 4))


class LocalhostCookiePolicy(DefaultCookiePolicy):
    """仅复现浏览器的localhost Secure例外，不修改服务端Cookie属性。"""
    def return_ok_secure(self, cookie, request):
        url = urlparse(request.get_full_url())
        if url.scheme == "http" and url.hostname == "localhost":
            return True
        return super().return_ok_secure(cookie, request)


class LocalhostBrowserSession(requests.Session):
    def __init__(self):
        super().__init__()
        self.trust_env = False
        self.cookies.set_policy(LocalhostCookiePolicy())

    def prepare_request(self, request):
        prepared = super().prepare_request(request)
        # requests默认会合并到新CookieJar而丢失policy，按原Jar重新计算，不绕过domain/path校验。
        prepared.headers.pop("Cookie", None)
        prepared.prepare_cookies(self.cookies)
        return prepared


class LoginForm(HTMLParser):
    def __init__(self, form_id="kc-form-login"):
        super().__init__()
        self.form_id = form_id
        self.action = None
        self.hidden = {}

    def handle_starttag(self, tag, attrs):
        attributes = dict(attrs)
        if tag == "form" and attributes.get("id") == self.form_id:
            self.action = attributes["action"]
        if tag == "input" and attributes.get("type") == "hidden" and attributes.get("name"):
            self.hidden[attributes["name"]] = attributes.get("value", "")


class KeycloakPocTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.config = json.loads(CONFIG.read_text(encoding="utf-8"))
        cls.http = requests.Session()
        cls.http.trust_env = False
        cls.discovery = cls.send(cls.http, "GET", cls.config["issuer"] + "/.well-known/openid-configuration").json()
        cls.keys = cls.send(cls.http, "GET", cls.discovery["jwks_uri"]).json()["keys"]

    @staticmethod
    def send(session, method, url, **kwargs):
        try:
            return session.request(method, url, timeout=15, **kwargs)
        except requests.RequestException:
            raise RuntimeError("PoC IdP transport failed; URL/credentials suppressed") from None

    def authorization_code(self, challenge=None):
        browser = LocalhostBrowserSession()
        verifier = secrets.token_urlsafe(48)
        state, nonce = secrets.token_urlsafe(24), secrets.token_urlsafe(24)
        digest = base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).rstrip(b"=").decode()
        response = self.send(browser, "GET", self.discovery["authorization_endpoint"], params={
            "client_id": self.config["webClientId"], "response_type": "code", "scope": "openid workbench:access",
            "redirect_uri": self.config["bffUrl"] + "/login/oauth2/code/workbench", "state": state,
            "nonce": nonce, "code_challenge_method": "S256", "code_challenge": challenge or digest,
        }, allow_redirects=False)
        self.assertEqual(response.status_code, 200)
        form = LoginForm()
        form.feed(response.text)
        self.assertIsNotNone(form.action)
        self.assertEqual(urlparse(form.action).netloc, urlparse(self.config["issuer"]).netloc)
        data = {**form.hidden, "username": self.config["username"], "password": self.config["password"]}
        login = self.send(browser, "POST", form.action, data=data, allow_redirects=False)
        self.assertEqual(login.status_code, 302)
        location = urlparse(login.headers["Location"])
        self.assertEqual(location.netloc, urlparse(self.config["bffUrl"]).netloc)
        self.assertEqual(location.path, "/login/oauth2/code/workbench")
        query = parse_qs(location.query)
        self.assertEqual(query.get("state"), [state])
        self.assertIn("code", query)
        return query["code"][0], verifier, nonce

    def exchange(self, code, verifier):
        return self.send(self.http, "POST", self.discovery["token_endpoint"],
                         auth=(self.config["webClientId"], self.config["webClientSecret"]), data={
                             "grant_type": "authorization_code", "code": code, "code_verifier": verifier,
                             "redirect_uri": self.config["bffUrl"] + "/login/oauth2/code/workbench"})

    def verify(self, token, expected_audience):
        header_part, payload_part, signature = token.split(".")
        header, payload = json.loads(unbase64(header_part)), json.loads(unbase64(payload_part))
        self.assertEqual(header["alg"], "RS256")
        key = next(key for key in self.keys if key["kid"] == header["kid"])
        public = rsa.RSAPublicNumbers(int.from_bytes(unbase64(key["e"]), "big"),
                                     int.from_bytes(unbase64(key["n"]), "big")).public_key()
        public.verify(unbase64(signature), (header_part + "." + payload_part).encode(), padding.PKCS1v15(), hashes.SHA256())
        self.assertEqual(payload["iss"], self.config["issuer"])
        audience = payload["aud"] if isinstance(payload["aud"], list) else [payload["aud"]]
        self.assertIn(expected_audience, audience)
        self.assertGreater(payload["exp"], time.time())
        return payload

    def test_code_pkce_claims_refresh_revocation_and_offline_residual(self):
        code, verifier, nonce = self.authorization_code()
        response = self.exchange(code, verifier)
        self.assertEqual(response.status_code, 200)
        tokens = response.json()
        access = self.verify(tokens["access_token"], "workbench-api")
        identity = self.verify(tokens["id_token"], self.config["webClientId"])
        self.assertEqual(identity["nonce"], nonce)
        self.assertEqual(access["sub"], self.config["subject"])
        self.assertEqual(access["azp"], self.config["webClientId"])
        self.assertEqual(access["exp"] - access["iat"], 604800)
        self.assertIn("workbench:access", access["scope"].split())
        self.assertNotIn("realm_access", access)
        self.assertNotIn("resource_access", access)
        refreshed = self.send(self.http, "POST", self.discovery["token_endpoint"],
                              auth=(self.config["webClientId"], self.config["webClientSecret"]),
                              data={"grant_type": "refresh_token", "refresh_token": tokens["refresh_token"]})
        self.assertEqual(refreshed.status_code, 200)
        current = refreshed.json()
        self.assertNotEqual(tokens["refresh_token"], current["refresh_token"])
        reused = self.send(self.http, "POST", self.discovery["token_endpoint"],
                           auth=(self.config["webClientId"], self.config["webClientSecret"]),
                           data={"grant_type": "refresh_token", "refresh_token": tokens["refresh_token"]})
        self.assertEqual(reused.status_code, 400)
        revoke = self.send(self.http, "POST", self.discovery["revocation_endpoint"],
                           auth=(self.config["webClientId"], self.config["webClientSecret"]),
                           data={"token": current["refresh_token"], "token_type_hint": "refresh_token"})
        self.assertEqual(revoke.status_code, 200)
        denied = self.send(self.http, "POST", self.discovery["token_endpoint"],
                           auth=(self.config["webClientId"], self.config["webClientSecret"]),
                           data={"grant_type": "refresh_token", "refresh_token": current["refresh_token"]})
        self.assertEqual(denied.status_code, 400)
        # 吊销刷新后的旧Access签名/exp仍有效，必须与在线撤销状态区分。
        self.verify(current["access_token"], "workbench-api")

    def test_authorization_code_cannot_be_reused(self):
        code, verifier, _ = self.authorization_code()
        self.assertEqual(self.exchange(code, verifier).status_code, 200)
        self.assertEqual(self.exchange(code, verifier).status_code, 400)

    def test_parallel_refresh_with_same_token_requires_bff_coordination(self):
        code, verifier, _ = self.authorization_code()
        issued = self.exchange(code, verifier)
        self.assertEqual(issued.status_code, 200)
        refresh = issued.json()["refresh_token"]
        barrier = threading.Barrier(2)

        def refresh_once():
            session = requests.Session()
            session.trust_env = False
            barrier.wait(timeout=10)
            return self.send(session, "POST", self.discovery["token_endpoint"],
                             auth=(self.config["webClientId"], self.config["webClientSecret"]),
                             data={"grant_type": "refresh_token", "refresh_token": refresh}).status_code

        with ThreadPoolExecutor(max_workers=2) as executor:
            results = list(executor.map(lambda _: refresh_once(), range(2)))
        # 不假定IdP能替BFF串行化；只验证每次响应均符合协议，记录成功数用于分析竞态。
        self.assertTrue(all(status in (200, 400) for status in results))
        self.assertIn(200, results)
        print("parallel_refresh_successes=" + str(results.count(200)))

    def test_wrong_pkce_and_legacy_password_grant_are_rejected(self):
        code, verifier, _ = self.authorization_code()
        self.assertEqual(self.exchange(code, verifier + "wrong").status_code, 400)
        response = self.send(self.http, "POST", self.discovery["token_endpoint"],
                             auth=(self.config["webClientId"], self.config["webClientSecret"]),
                             data={"grant_type": "password", "username": self.config["username"],
                                   "password": self.config["password"]})
        self.assertEqual(response.status_code, 400)

    def test_machine_identity_and_invalid_client_credentials(self):
        response = self.send(self.http, "POST", self.discovery["token_endpoint"],
                             auth=(self.config["taskClientId"], self.config["taskClientSecret"]),
                             data={"grant_type": "client_credentials"})
        self.assertEqual(response.status_code, 200)
        token = response.json()
        payload = self.verify(token["access_token"], "workbench-auth-internal")
        self.assertEqual(payload["azp"], self.config["taskClientId"])
        self.assertEqual(payload["exp"] - payload["iat"], 300)
        self.assertIn("capability:task:issue", payload["scope"].split())
        self.assertNotIn("workbench:access", payload["scope"].split())
        self.assertNotIn("refresh_token", token)
        wrong = self.send(self.http, "POST", self.discovery["token_endpoint"],
                          auth=(self.config["taskClientId"], secrets.token_urlsafe(32)),
                          data={"grant_type": "client_credentials"})
        self.assertEqual(wrong.status_code, 401)


if __name__ == "__main__":
    unittest.main(verbosity=2)
