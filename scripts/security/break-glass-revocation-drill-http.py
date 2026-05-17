#!/usr/bin/env python3
"""HTTP helpers for break-glass revocation drill — never print tokens."""

from __future__ import annotations

import base64
import json
import ssl
import sys
import urllib.error
import urllib.request
from typing import Any


def _request(
    method: str,
    url: str,
    bearer: str | None = None,
    body: dict[str, Any] | None = None,
) -> tuple[int, dict[str, Any] | str]:
    data = None
    headers = {"Accept": "application/json"}
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    if bearer:
        headers["Authorization"] = f"Bearer {bearer}"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    ctx = ssl.create_default_context()
    try:
        with urllib.request.urlopen(req, context=ctx, timeout=60) as resp:
            raw = resp.read().decode("utf-8")
            status = resp.status
    except urllib.error.HTTPError as e:
        status = e.code
        raw = e.read().decode("utf-8", errors="replace")
    try:
        parsed: dict[str, Any] | str = json.loads(raw) if raw else {}
    except json.JSONDecodeError:
        parsed = raw
    return status, parsed


def decode_jwt_claims(token: str) -> dict[str, Any]:
    parts = token.split(".")
    if len(parts) < 2:
        return {}
    payload = parts[1] + "=" * (-len(parts[1]) % 4)
    data = base64.urlsafe_b64decode(payload.encode("ascii"))
    return json.loads(data.decode("utf-8"))


def login(base_url: str, emergency_token: str, reason: str) -> tuple[int, str]:
    status, body = _request(
        "POST",
        f"{base_url.rstrip('/')}/auth/break-glass/login",
        body={"token": emergency_token, "reason": reason},
    )
    if status >= 400 or not isinstance(body, dict):
        return status, ""
    return status, str(body.get("accessToken") or "")


def list_sessions(base_url: str, admin_token: str) -> tuple[int, dict[str, Any]]:
    status, body = _request(
        "GET",
        f"{base_url.rstrip('/')}/admin/security/break-glass/sessions",
        bearer=admin_token,
    )
    if not isinstance(body, dict):
        return status, {}
    return status, body


def revoke_session(
    base_url: str, admin_token: str, revoke_ref: str, reason: str
) -> tuple[int, dict[str, Any]]:
    status, body = _request(
        "POST",
        f"{base_url.rstrip('/')}/admin/security/break-glass/sessions/{revoke_ref}/revoke",
        bearer=admin_token,
        body={"reason": reason},
    )
    if not isinstance(body, dict):
        return status, {}
    return status, body


def probe_endpoint(base_url: str, path: str, bearer: str) -> tuple[int, dict[str, Any] | str]:
    url = base_url.rstrip("/") + path
    return _request("GET", url, bearer=bearer)


def main() -> int:
    action = sys.argv[1] if len(sys.argv) > 1 else ""
    if action == "login":
        base, token, reason = sys.argv[2], sys.argv[3], sys.argv[4]
        status, access = login(base, token, reason)
        print(json.dumps({"httpStatus": status, "hasAccessToken": bool(access)}))
        if access:
            # token only on stdout for parent pipe — parent must not log
            print(access)
        return 0 if access else 1
    if action == "claims":
        claims = decode_jwt_claims(sys.argv[2])
        safe = {
            "jti": claims.get("jti", ""),
            "break_glass_session_id": claims.get("break_glass_session_id", ""),
            "exp": claims.get("exp"),
            "iat": claims.get("iat"),
        }
        print(json.dumps(safe))
        return 0
    if action == "list":
        status, body = list_sessions(sys.argv[2], sys.argv[3])
        print(json.dumps({"httpStatus": status, "body": body}))
        return 0
    if action == "revoke":
        status, body = revoke_session(sys.argv[2], sys.argv[3], sys.argv[4], sys.argv[5])
        print(json.dumps({"httpStatus": status, "body": body}))
        return 0
    if action == "probe":
        status, body = probe_endpoint(sys.argv[2], sys.argv[3], sys.argv[4])
        code = ""
        if isinstance(body, dict):
            code = str(body.get("code") or body.get("errorCode") or "")
        print(json.dumps({"httpStatus": status, "errorCode": code}))
        return 0
    print("unknown action", file=sys.stderr)
    return 2


if __name__ == "__main__":
    raise SystemExit(main())
