"""P0浏览器HTTP驱动：通过stdin/stdout与测试进程通信，不向终端打印会话/授权码。"""
from pathlib import Path
from urllib.parse import urlparse
import importlib.util
import json
import sys


sys.dont_write_bytecode = True
spec = importlib.util.spec_from_file_location("poc_protocol", Path(__file__).with_name("Test-PocIdentity.py"))
protocol = importlib.util.module_from_spec(spec)
spec.loader.exec_module(protocol)
config = json.loads(protocol.CONFIG.read_text(encoding="utf-8"))
browser = protocol.LocalhostBrowserSession()


def call(method, url, **kwargs):
    if urlparse(url).netloc != urlparse(config["issuer"]).netloc or not url.startswith(config["issuer"] + "/"):
        raise RuntimeError("Unexpected browser target")
    return protocol.KeycloakPocTest.send(browser, method, url, allow_redirects=False, **kwargs)


for line in sys.stdin:
    try:
        command = json.loads(line)
        if command["action"] == "stop":
            break
        response = call("GET", command["url"])
        used_form = False
        if command["action"] == "authorize" and response.status_code == 200:
            form = protocol.LoginForm()
            form.feed(response.text)
            if not form.action:
                raise RuntimeError("Expected identity login form")
            response = call("POST", form.action, data={**form.hidden, "username": config["username"], "password": config["password"]})
            used_form = True
        location = response.headers.get("Location", "")
        if location and not location.startswith(config["bffUrl"] + "/"):
            raise RuntimeError("Unexpected browser callback")
        print(json.dumps({"status": response.status_code, "callback": location, "usedLoginForm": used_form}), flush=True)
    except Exception as error:
        print(json.dumps({"error": type(error).__name__}), flush=True)
