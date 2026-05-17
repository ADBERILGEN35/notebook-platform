#!/usr/bin/env python3
"""
Build sanitized SCIM delta staging sandbox summary + GitHub Step Summary (Faz 121).
No tokens, raw SCIM payloads, cursors, or PII in output.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

EVIDENCE_NAME = "scim-delta-sandbox-evidence.json"
SUMMARY_NAME = "scim-delta-sandbox-summary.md"
CHECKLIST_NAME = "scim-delta-certification-checklist.md"


def load_evidence(output_dir: Path) -> dict[str, Any]:
    path = output_dir / EVIDENCE_NAME
    if not path.is_file():
        alt = output_dir / "scim-delta-remote-fetch-evidence.json"
        if alt.is_file():
            return json.loads(alt.read_text(encoding="utf-8"))
        raise FileNotFoundError(f"missing evidence: {path}")
    return json.loads(path.read_text(encoding="utf-8"))


def derive_certification(evidence: dict[str, Any], expect_ready: bool) -> str:
    status = evidence.get("evidenceStatus")
    if status == "skipped":
        return "skipped"
    if status == "privacy_violation":
        return "blocked"
    hints = evidence.get("certificationHints") or {}
    if hints.get("privacyChecksPassed") is False:
        return "blocked"
    if evidence.get("dryRunOnly") is not True:
        return "blocked"
    if hints.get("missingFromDeltaNoDeprovision") is not True:
        return "blocked"

    if status != "passed":
        return "needs-review"

    if evidence.get("remoteFetchEnabled"):
        if hints.get("onePageGetAttempted") is not True:
            return "needs-review"
        if hints.get("paginationObservedOrValidStop") is not True:
            return "needs-review"
        if hints.get("rateLimitDiagnosticReady") is not True:
            return "needs-review"
        pec = evidence.get("providerErrorClass") or "NONE"
        if pec == "PROVIDER_AUTH_FAILED":
            return "blocked"
        if pec not in ("NONE", "RATE_LIMITED", "RETRY_AFTER_OBSERVED", "PROVIDER_UNAVAILABLE", "TIMEOUT", "PROVIDER_BAD_RESPONSE"):
            return "needs-review"

    return "certified"


def exit_code_for(certification: str, evidence: dict[str, Any]) -> int:
    if certification == "skipped":
        return 0
    if certification == "blocked":
        return 5
    if evidence.get("evidenceStatus") == "privacy_violation":
        return 3
    if certification == "needs-review":
        return 0
    return 0


def readiness_gap(expect_ready: bool, certification: str) -> bool:
    return expect_ready and certification not in ("certified", "skipped")


def write_summary(
    output_dir: Path,
    evidence: dict[str, Any],
    certification: str,
    provider: str,
    expect_ready: bool,
    skip_reason: str | None,
) -> None:
    warnings = evidence.get("warnings") or []
    lines = [
        "# SCIM delta staging sandbox summary",
        "",
        f"Generated: {datetime.now(timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ')}",
        "",
        "> Sanitized aggregate only. No tokens, raw SCIM bodies, or cursor values.",
        "",
        "## Run",
        "",
        f"| Field | Value |",
        f"|-------|--------|",
        f"| Provider (input) | {provider} |",
        f"| Certification result | **{certification}** |",
        f"| Expect ready | {str(expect_ready).lower()} |",
        f"| Evidence status | {evidence.get('evidenceStatus', 'n/a')} |",
        f"| Skip reason | {skip_reason or evidence.get('skipReason') or 'n/a'} |",
        "",
        "## Dry-run diagnostics",
        "",
        f"| Field | Value |",
        f"|-------|--------|",
        f"| Provider type | {evidence.get('providerType', 'n/a')} |",
        f"| Selected strategy | {evidence.get('selectedStrategy', 'n/a')} |",
        f"| Remote fetch enabled | {evidence.get('remoteFetchEnabled', 'n/a')} |",
        f"| Remote fetch configured | {evidence.get('remoteFetchConfigured', 'n/a')} |",
        f"| Remote fetch attempted | {evidence.get('remoteFetchAttempted', 'n/a')} |",
        f"| Multi-page enabled | {evidence.get('remoteMultiPageEnabled', 'n/a')} |",
        f"| Pages observed | {evidence.get('pagesObserved', 'n/a')} |",
        f"| Fetched resource count | {evidence.get('fetchedResourceCount', 'n/a')} |",
        f"| Stopped reason | {evidence.get('stoppedReason', 'n/a')} |",
        f"| Next cursor present | {evidence.get('nextCursorPresent', 'n/a')} |",
        f"| Provider error class | {evidence.get('providerErrorClass', 'n/a')} |",
        f"| Retry-After seconds | {evidence.get('retryAfterSeconds', 'n/a')} |",
        f"| Warnings count | {len(warnings)} |",
        "",
        "## Warnings (symbolic)",
        "",
    ]
    if warnings:
        for w in warnings[:30]:
            lines.append(f"- `{w}`")
    else:
        lines.append("- none")
    lines.extend(
        [
            "",
            "## CR handoff",
            "",
            "Attach artifacts from this run:",
            "",
            "- `scim-delta-sandbox-evidence.json`",
            "- `scim-delta-certification-checklist.md`",
            "- `scim-delta-sandbox-summary.md`",
            "",
            "Do not attach raw IdP responses, bearer tokens, or cursor values.",
            "",
        ]
    )
    (output_dir / SUMMARY_NAME).write_text("\n".join(lines) + "\n", encoding="utf-8")


def render_github_summary(
    evidence: dict[str, Any],
    certification: str,
    provider: str,
) -> None:
    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
    if not summary_path:
        return
    warnings = evidence.get("warnings") or []
    md = f"""## SCIM delta sandbox evidence

| Field | Value |
|-------|--------|
| Provider | {provider} |
| Strategy | {evidence.get('selectedStrategy', 'n/a')} |
| Remote fetch enabled | {evidence.get('remoteFetchEnabled', 'n/a')} |
| Remote fetch configured | {evidence.get('remoteFetchConfigured', 'n/a')} |
| Remote fetch attempted | {evidence.get('remoteFetchAttempted', 'n/a')} |
| Pages observed | {evidence.get('pagesObserved', 'n/a')} |
| Fetched resource count | {evidence.get('fetchedResourceCount', 'n/a')} |
| Stopped reason | {evidence.get('stoppedReason', 'n/a')} |
| Provider error class | {evidence.get('providerErrorClass', 'n/a')} |
| Warnings count | {len(warnings)} |
| **Certification result** | **{certification}** |

"""
    Path(summary_path).write_text(md, encoding="utf-8")


def write_skip_bundle(output_dir: Path, provider: str, reason: str, expect_ready: bool) -> int:
    evidence: dict[str, Any] = {
        "evidenceSchemaVersion": "scim-delta-evidence-v1",
        "generatedAt": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "evidenceStatus": "skipped",
        "skipReason": reason,
        "providerType": provider if provider in ("okta", "azure-ad", "generic") else "unknown",
        "selectedStrategy": "NONE",
        "remoteFetchEnabled": False,
        "remoteFetchConfigured": False,
        "remoteFetchAttempted": False,
        "remoteMultiPageEnabled": False,
        "dryRunOnly": True,
        "pagesObserved": 0,
        "fetchedResourceCount": 0,
        "nextCursorPresent": False,
        "stoppedReason": "NOT_CONFIGURED",
        "pageLimitReached": False,
        "resourceLimitReached": False,
        "providerErrorClass": "NONE",
        "retryAfterSeconds": None,
        "nextRecommendedAttemptAt": None,
        "warnings": ["SCIM_DELTA_REMOTE_FETCH_NOT_CONFIGURED"],
        "certificationHints": {
            "privacyChecksPassed": True,
            "dryRunOnlyConfirmed": True,
            "onePageGetAttempted": False,
            "paginationObservedOrValidStop": False,
            "rateLimitDiagnosticReady": False,
            "missingFromDeltaNoDeprovision": True,
        },
        "certificationResult": "skipped",
    }
    (output_dir / EVIDENCE_NAME).write_text(
        json.dumps(evidence, indent=2) + "\n", encoding="utf-8"
    )
    checklist = (
        f"# SCIM delta certification — skipped\n\nReason: {reason}\n\n"
        "Result: **skipped**\n"
    )
    (output_dir / CHECKLIST_NAME).write_text(checklist, encoding="utf-8")
    write_summary(output_dir, evidence, "skipped", provider, expect_ready, reason)
    return 0


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output-dir", required=True)
    parser.add_argument("--provider", default="generic")
    parser.add_argument("--expect-ready", action="store_true")
    parser.add_argument("--skip", default="")
    parser.add_argument("--github-summary", action="store_true")
    args = parser.parse_args()

    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    if args.skip:
        write_skip_bundle(output_dir, args.provider, args.skip, args.expect_ready)
        if args.github_summary:
            evidence = load_evidence(output_dir)
            render_github_summary(evidence, "skipped", args.provider)
        print("0")
        return 0

    evidence = load_evidence(output_dir)
    certification = derive_certification(evidence, args.expect_ready)
    evidence["certificationResult"] = certification
    (output_dir / EVIDENCE_NAME).write_text(
        json.dumps(evidence, indent=2) + "\n", encoding="utf-8"
    )
    write_summary(
        output_dir,
        evidence,
        certification,
        args.provider,
        args.expect_ready,
        evidence.get("skipReason"),
    )

    if args.github_summary:
        render_github_summary(evidence, certification, args.provider)

    code = exit_code_for(certification, evidence)
    if readiness_gap(args.expect_ready, certification):
        print("2")
        return 2
    print(str(code))
    return code


if __name__ == "__main__":
    raise SystemExit(main())
