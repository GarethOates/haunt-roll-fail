#!/usr/bin/env python3 -I
"""
Deploy hrf-gate.js as the Cloudflare Worker "hrf-gate" on hrf.botc.games/*.

Usage:
    python3 -I deploy/cloudflare/deploy-gate.py [--code] [--new-key]

  --code      ask for a new code (not echoed) and store it as the Worker secret HRF_CODE
  --new-key   store a new random SIGNING_KEY: every visitor has to enter the code again

The first deploy needs both. Later uploads of the script keep the secrets. The API token is read from the
macOS Keychain (security find-generic-password -s cloudflare-hrf-token) and needs Workers Scripts and
Workers Routes edit. It also adds the routes in BYPASS, which skip the Worker.
"""

import argparse
import getpass
import json
import os
import secrets
import subprocess
import sys
import urllib.request
import uuid

ACCOUNT = "bf1298b7b7a088d7ad550bebdf4799be"
ZONE = "b90269a6ae1440ed4826e953180b4430"
SCRIPT = "hrf-gate"
PATTERN = "hrf.botc.games/*"
# routes with no Worker: an open game polls /read twice a second, which would use up the free plan's 100k Worker
# requests a day; /read needs the player's own user id and secret, so it does not need the code as well
BYPASS = ["hrf.botc.games/read/*"]
API = "https://api.cloudflare.com/client/v4/"
SOURCE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "hrf-gate.js")


def token():
    return subprocess.run(["security", "find-generic-password", "-s", "cloudflare-hrf-token", "-w"],
                          capture_output=True, text=True, check=True).stdout.strip()


def call(method, path, body=None, content_type="application/json"):
    data = body if isinstance(body, (bytes, type(None))) else json.dumps(body).encode()
    req = urllib.request.Request(API + path, data=data, method=method,
                                 headers={"Authorization": "Bearer " + token(), "Content-Type": content_type})
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            res = json.load(r)
    except urllib.error.HTTPError as e:
        res = json.load(e)
    if not res.get("success"):
        sys.exit("%s %s failed: %s" % (method, path, [x.get("message") for x in res.get("errors", [])]))
    return res["result"]


def upload():
    boundary = uuid.uuid4().hex
    meta = {"main_module": "hrf-gate.js", "compatibility_date": "2025-09-01", "keep_bindings": ["secret_text"]}
    with open(SOURCE, "rb") as f:
        code = f.read()
    parts = [
        ("metadata", "metadata.json", "application/json", json.dumps(meta).encode()),
        ("hrf-gate.js", "hrf-gate.js", "application/javascript+module", code),
    ]
    body = b""
    for name, filename, ctype, data in parts:
        body += ("--%s\r\nContent-Disposition: form-data; name=\"%s\"; filename=\"%s\"\r\nContent-Type: %s\r\n\r\n" % (boundary, name, filename, ctype)).encode() + data + b"\r\n"
    body += ("--%s--\r\n" % boundary).encode()
    call("PUT", "accounts/%s/workers/scripts/%s" % (ACCOUNT, SCRIPT), body, "multipart/form-data; boundary=" + boundary)
    print("uploaded %s (%d bytes)" % (SCRIPT, len(code)))


def secret(name, value):
    call("PUT", "accounts/%s/workers/scripts/%s/secrets" % (ACCOUNT, SCRIPT), {"name": name, "text": value, "type": "secret_text"})
    print("set secret " + name)


def route():
    routes = call("GET", "zones/%s/workers/routes" % ZONE)
    if any(r["pattern"] == PATTERN and r.get("script") == SCRIPT for r in routes):
        print("route %s already in place" % PATTERN)
    else:
        call("POST", "zones/%s/workers/routes" % ZONE, {"pattern": PATTERN, "script": SCRIPT})
        print("route %s -> %s" % (PATTERN, SCRIPT))
    for pattern in BYPASS:
        if any(r["pattern"] == pattern and not r.get("script") for r in routes):
            print("route %s (no Worker) already in place" % pattern)
        else:
            call("POST", "zones/%s/workers/routes" % ZONE, {"pattern": pattern})
            print("route %s -> no Worker" % pattern)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--code", action="store_true", help="ask for a new code")
    ap.add_argument("--new-key", action="store_true", help="new cookie signing key (logs everyone out)")
    args = ap.parse_args()

    code = None
    if args.code:
        code = os.environ.get("HRF_CODE") or getpass.getpass("New code: ")
        if len(code.strip()) < 4:
            sys.exit("the code is too short")

    upload()
    if code:
        secret("HRF_CODE", code.strip())
    if args.new_key:
        secret("SIGNING_KEY", secrets.token_urlsafe(32))
    route()


if __name__ == "__main__":
    main()
