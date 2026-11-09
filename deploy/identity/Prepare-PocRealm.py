"""仅准备独立P0身份夹具；不管理业务用户，不启动/重建容器，不输出凭据。"""
from pathlib import Path
import argparse
import json
import os
import secrets
import subprocess
import tempfile

import requests


ROOT = Path(__file__).resolve().parents[2]
CONFIG = ROOT / ".codex-validation-store/v0.3.0-p0/runtime/oidc-poc.json"
ISSUER = "http://localhost:18090/realms/agent-doc-workbench-p0"
REALM = "agent-doc-workbench-p0"


def require(response, status):
    if response.status_code != status:
        raise RuntimeError(f"Identity fixture operation failed: HTTP {response.status_code}, expected {status}")
    return response


def admin_session():
    """仅供独立测试夹具：bootstrap凭据和管理员Token都只在内存使用。"""
    container = json.loads(subprocess.check_output(
        ["docker", "inspect", "adw-identity-keycloak"], encoding="utf-8"))[0]
    environment = dict(item.split("=", 1) for item in container["Config"]["Env"] if "=" in item)
    http = requests.Session()
    http.trust_env = False
    token = require(http.post("http://localhost:18090/realms/master/protocol/openid-connect/token", data={
        "grant_type": "password", "client_id": "admin-cli",
        "username": environment["KC_BOOTSTRAP_ADMIN_USERNAME"],
        "password": environment["KC_BOOTSTRAP_ADMIN_PASSWORD"],
    }, timeout=15), 200).json()["access_token"]
    return http, {"Authorization": "Bearer " + token}


def prepare(logout_fixture_user=False):
    http, headers = admin_session()
    realm_url = "http://localhost:18090/admin/realms/" + REALM
    existing = http.get(realm_url, headers=headers, timeout=15)
    if existing.status_code == 200:
        if existing.json().get("attributes", {}).get("adw.p0.fixture") != "v1" or not CONFIG.exists():
            raise RuntimeError("Existing realm was not provisioned by this fixture; no changes made")
        config = json.loads(CONFIG.read_text(encoding="utf-8"))
        if config["issuer"] != ISSUER:
            raise RuntimeError("Fixture issuer mismatch; no changes made")
        if logout_fixture_user:
            require(http.post(realm_url + "/users/" + config["subject"] + "/logout", headers=headers, timeout=15), 204)
            print(json.dumps({"realm": REALM, "status": "fixture-user-sessions-cleared", "credentials_printed": False}))
            return
        # 仅同步本工具创建的测试realm刷新策略，不覆盖账号、客户端或其他管理侧配置。
        require(http.put(realm_url, headers=headers,
                         json={"revokeRefreshToken": True, "refreshTokenMaxReuse": 0}, timeout=15), 204)
        # basic仅含sub/auth_time，避免裁剪默认scope后丢失必需身份；不加入角色mapper。
        scope_url = realm_url + "/client-scopes"
        scopes = require(http.get(scope_url, headers=headers, timeout=15), 200).json()
        basic = next((scope for scope in scopes if scope["name"] == "basic"), None)
        if basic is None:
            source = json.loads((ROOT / "deploy/identity/realm/agent-doc-workbench-p0-realm.json").read_text(encoding="utf-8"))
            require(http.post(scope_url, headers=headers,
                              json=next(scope for scope in source["clientScopes"] if scope["name"] == "basic"), timeout=15), 201)
            scopes = require(http.get(scope_url, headers=headers, timeout=15), 200).json()
            basic = next(scope for scope in scopes if scope["name"] == "basic")
        for client_id in (config["webClientId"], config["taskClientId"]):
            clients = require(http.get(realm_url + "/clients", headers=headers,
                                       params={"clientId": client_id}, timeout=15), 200).json()
            if len(clients) != 1:
                raise RuntimeError("Fixture client missing or ambiguous")
            require(http.put(realm_url + "/clients/" + clients[0]["id"] + "/default-client-scopes/" + basic["id"],
                             headers=headers, timeout=15), 204)
        print(json.dumps({"realm": REALM, "status": "reused", "credentials_printed": False}))
        return
    require(existing, 404)
    if logout_fixture_user:
        raise RuntimeError("Fixture realm missing; no changes made")
    config = {
        "issuer": ISSUER, "bffUrl": "http://localhost:18091",
        "webClientId": "workbench-web", "webClientSecret": secrets.token_urlsafe(32),
        "taskClientId": "workbench-task", "taskClientSecret": secrets.token_urlsafe(32),
        "username": "p0-user", "password": secrets.token_urlsafe(32),
        "subject": "f2dc9db0-b624-4cc9-a234-26d0e3e018a7",
    }
    template = (ROOT / "deploy/identity/realm/agent-doc-workbench-p0-realm.json").read_text(encoding="utf-8")
    substitutions = {
        "KEYCLOAK_POC_BFF_URL": config["bffUrl"],
        "KEYCLOAK_POC_WEB_CLIENT_SECRET": config["webClientSecret"],
        "KEYCLOAK_POC_TASK_CLIENT_SECRET": config["taskClientSecret"],
        "KEYCLOAK_POC_USER_PASSWORD": config["password"],
    }
    for key, value in substitutions.items():
        template = template.replace("${" + key + "}", value)
    # 先保存本机夹具凭据，远程失败时仍能重试/诊断；不持久化master管理员凭据或Token。
    CONFIG.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", dir=CONFIG.parent, delete=False) as output:
        temporary = Path(output.name)
        json.dump(config, output)
    os.replace(temporary, CONFIG)
    response = http.post("http://localhost:18090/admin/realms", headers=headers,
                         json=json.loads(template), timeout=45)
    require(response, 201)
    representation = require(http.get(realm_url, headers=headers, timeout=15), 200).json()
    print(json.dumps({"realm": REALM, "status": "created",
                      "keycloak_version": require(http.get("http://localhost:18090/admin/serverinfo",
                                                           headers=headers, timeout=15), 200).json()["systemInfo"]["version"],
                      "access_ttl": representation["accessTokenLifespan"],
                      "registration": representation["registrationAllowed"], "credentials_printed": False}))


if __name__ == "__main__":
    arguments = argparse.ArgumentParser(description=__doc__)
    arguments.add_argument("--logout-fixture-user", action="store_true", help="仅清理本工具独立测试用户的IdP会话")
    prepare(arguments.parse_args().logout_fixture_user)
