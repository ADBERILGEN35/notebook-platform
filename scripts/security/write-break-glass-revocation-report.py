#!/usr/bin/env python3
"""Sanitized break-glass revocation drill evidence (Faz 123)."""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

EVIDENCE_NAME = "break-glass-revocation-evidence.json"
SUMMARY_NAME = "break-glass-revocation-summary.md"
SCHEMA = "break-glass-revocation-evidence-v1"

FORBIDDEN = re.compile(
    r"Bearer [A-Za-z0-9._-]{8,}|eyJ[A-Za-z0-9_-]{10,}|\"access_token\"|"
    r"Authorization:|password|emergency.token|\"secret\"",
    re.IGNORECASE,
)


def mask_jti(jti: str) -> str:
    if not jti:
        return ""
    jti = jti.strip()
    if len(jti) <= 8:
        return "****"
    return f"{jti[:4]}…{jti[-4:]}"


def mask_ref(ref: str) -> str:
    if not ref:
        return ""
    ref = ref.strip()
    if len(ref) <= 12:
        return ref[:4] + "…" if len(ref) > 4 else "****"
    return ref[:8] + "…"


def short_session(session_id: str) -> str:
    if not session_id:
        return ""
    return session_id if len(session_id) <= 12 else session_id[:12]


def privacy_scan(text: str) -> bool:
    return bool(FORBIDDEN.search(text))


def build_skip_bundle(output_dir: Path, reason: str, environment: str) -> dict[str, Any]:
    return {
        "evidenceSchemaVersion": SCHEMA,
        "generatedAt": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "environment": environment,
        "gitSha": os.environ.get("GIT_SHA", "unknown"),
        "imageTag": os.environ.get("IMAGE_TAG", "unknown"),
        "result": "skipped",
        "skipReason": reason,
        "issuedAt": None,
        "expiresAt": None,
        "sessionIdShort": "",
        "jtiMasked": "",
        "revokeRefMasked": "",
        "revokedAt": None,
        "revokedByPresent": False,
        "reasonPresent": False,
        "gatewayRejectStatus": None,
        "gatewayRejectErrorCode": "",
        "auditEventsObserved": [],
        "metricsObserved": {},
        "readinessGaps": [],
        "drillSteps": {
            "loginAttempted": False,
            "sessionsListed": False,
            "revokeAttempted": False,
            "gatewayRejectVerified": False,
        },
    }


def write_summary(output_dir: Path, evidence: dict[str, Any]) -> None:
    lines = [
        "# Break-glass revocation staging drill summary",
        "",
        f"Generated: {evidence.get('generatedAt', 'n/a')}",
        "",
        "> Sanitized only. No tokens, JWT, or emergency secrets.",
        "",
        "## Result",
        "",
        f"| Field | Value |",
        f"|-------|--------|",
        f"| Result | **{evidence.get('result', 'n/a')}** |",
        f"| Environment | {evidence.get('environment', 'n/a')} |",
        f"| Git SHA | {evidence.get('gitSha', 'n/a')} |",
        f"| Image tag | {evidence.get('imageTag', 'n/a')} |",
        f"| Skip reason | {evidence.get('skipReason') or 'n/a'} |",
        "",
        "## Session (masked)",
        "",
        f"| Field | Value |",
        f"| Session (short) | {evidence.get('sessionIdShort', 'n/a')} |",
        f"| JTI (masked) | {evidence.get('jtiMasked', 'n/a')} |",
        f"| Revoke ref (masked) | {evidence.get('revokeRefMasked', 'n/a')} |",
        f"| Issued at | {evidence.get('issuedAt', 'n/a')} |",
        f"| Expires at | {evidence.get('expiresAt', 'n/a')} |",
        f"| Revoked at | {evidence.get('revokedAt', 'n/a')} |",
        "",
        "## Gateway denylist reject",
        "",
        f"| Field | Value |",
        f"| HTTP status | {evidence.get('gatewayRejectStatus', 'n/a')} |",
        f"| Error code | {evidence.get('gatewayRejectErrorCode', 'n/a')} |",
        "",
        "## Drill steps",
        "",
    ]
    steps = evidence.get("drillSteps") or {}
    for key, val in steps.items():
        lines.append(f"- {key}: {val}")
    gaps = evidence.get("readinessGaps") or []
    if gaps:
        lines.extend(["", "## Readiness gaps", ""])
        for g in gaps:
            lines.append(f"- `{g}`")
    (output_dir / SUMMARY_NAME).write_text("\n".join(lines) + "\n", encoding="utf-8")


def render_github_summary(evidence: dict[str, Any]) -> None:
    path = os.environ.get("GITHUB_STEP_SUMMARY")
    if not path:
        return
    md = f"""## Break-glass revocation drill

| Field | Value |
|-------|--------|
| Result | **{evidence.get('result')}** |
| Environment | {evidence.get('environment')} |
| Gateway reject status | {evidence.get('gatewayRejectStatus')} |
| Gateway error code | {evidence.get('gatewayRejectErrorCode')} |
| Session (short) | {evidence.get('sessionIdShort')} |
| JTI (masked) | {evidence.get('jtiMasked')} |
| Revoked by present | {evidence.get('revokedByPresent')} |
| Reason present | {evidence.get('reasonPresent')} |

"""
    Path(path).write_text(md, encoding="utf-8")


def exit_for_result(result: str) -> int:
    if result == "skipped":
        return 0
    if result == "privacy-failure":
        return 3
    if result == "failed":
        return 5
    if result == "readiness-gap":
        return 2
    if result == "shape-mismatch":
        return 4
    return 0


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output-dir", required=True)
    parser.add_argument("--environment", default="staging")
    parser.add_argument("--skip", default="")
    parser.add_argument("--evidence-json", default="", help="inline JSON for live run")
    parser.add_argument("--github-summary", action="store_true")
    args = parser.parse_args()

    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    if args.skip:
        evidence = build_skip_bundle(output_dir, args.skip, args.environment)
    elif args.evidence_json:
        evidence = json.loads(args.evidence_json)
        evidence.setdefault("evidenceSchemaVersion", SCHEMA)
    else:
        print("missing --skip or --evidence-json", file=sys.stderr)
        return 4

    (output_dir / EVIDENCE_NAME).write_text(
        json.dumps(evidence, indent=2) + "\n", encoding="utf-8"
    )
    write_summary(output_dir, evidence)
    combined = (output_dir / EVIDENCE_NAME).read_text(encoding="utf-8") + (
        output_dir / SUMMARY_NAME
    ).read_text(encoding="utf-8")
    if privacy_scan(combined):
        evidence["result"] = "privacy-failure"
        (output_dir / EVIDENCE_NAME).write_text(
            json.dumps(evidence, indent=2) + "\n", encoding="utf-8"
        )
        write_summary(output_dir, evidence)

    if args.github_summary:
        render_github_summary(evidence)

    code = exit_for_result(str(evidence.get("result", "")))
    print(str(code))
    return code


if __name__ == "__main__":
    raise SystemExit(main())
