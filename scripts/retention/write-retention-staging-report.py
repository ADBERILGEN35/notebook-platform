#!/usr/bin/env python3
"""
Build sanitized retention staging smoke JSON + Markdown reports (Faz 109).
No secrets, tokens, or raw API bodies in output.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

FORBIDDEN_MESSAGE_PATTERNS = re.compile(
    r"(?i)(Bearer\s|ADMIN_ACCESS_TOKEN|ACCESS_TOKEN=|"
    r"noteBody|commentBody|contentBlocks|userEmail|noteTitle|"
    r"recipientEmail|workspaceName|invitationEmail|"
    r"snippet|contentText|documentText|indexed content|"
    r'"targets"\s*:\s*\[|"payload"\s*:)'
)

ALLOWED_LINE_PREFIXES = (
    "Calling ",
    "Using fixture",
    "targets seen",
    "Warnings:",
    "serviceSummaries",
    "Readiness gap:",
    "Privacy guardrail",
    "Unexpected HTTP",
    "production-ready",
    "not enabled",
    "not enabled/available",
    "Content targets",
    "Notification targets",
    "Workspace targets",
    "Search targets",
    "Content retention",
    "Notification retention",
    "Workspace retention",
    "Search retention",
    "Response is not valid JSON",
    "Response missing required",
    "dry-run targets missing",
    "status ",
    "eligibleCount",
    "purgeableCount",
    "blockedByLegalHold",
)


def sanitize_message(exit_code: int, raw_output: str) -> str:
    lines: list[str] = []
    for line in raw_output.splitlines():
        stripped = line.strip()
        if not stripped:
            continue
        if FORBIDDEN_MESSAGE_PATTERNS.search(stripped):
            return "Sanitized summary withheld (potential sensitive content in script output)."
        if any(stripped.startswith(p) for p in ALLOWED_LINE_PREFIXES):
            lines.append(stripped[:240])
        elif exit_code != 0 and len(stripped) < 200 and "{" not in stripped and "@" not in stripped:
            lines.append(stripped[:240])
    if not lines:
        if exit_code == 0:
            return "Smoke completed successfully."
        return f"Smoke failed with exit code {exit_code}."
    return lines[-1]


def classify_status(exit_code: int, expect_ready: bool, message: str) -> str:
    if exit_code == 3:
        return "privacy-failure"
    if exit_code == 4:
        return "shape-failure"
    if exit_code == 2:
        return "readiness-gap"
    if exit_code == 0:
        lower = message.lower()
        if "production-ready" in lower:
            return "passed"
        if "not enabled" in lower or "expected pre-rollout" in lower:
            return "expected-gap"
        return "passed"
    return "shape-failure"


def domain_record(
    domain: str,
    exit_code: int,
    expect_ready: bool,
    raw_output: str,
) -> dict[str, Any]:
    message = sanitize_message(exit_code, raw_output)
    status = classify_status(exit_code, expect_ready, message)
    return {
        "domain": domain,
        "status": status,
        "exitCode": exit_code,
        "expectReady": expect_ready,
        "message": message,
    }


def skip_record(domain: str, reason: str) -> dict[str, Any]:
    return {
        "domain": domain,
        "status": "skipped",
        "exitCode": 0,
        "expectReady": None,
        "message": reason,
    }


def overall_from_domains(domains: list[dict[str, Any]], exit_code: int) -> dict[str, Any]:
    if all(d["status"] == "skipped" for d in domains):
        return {
            "status": "skipped",
            "exitCode": 0,
            "message": domains[0]["message"] if domains else "skipped",
        }
    statuses = {d["status"] for d in domains if d["status"] != "skipped"}
    if exit_code == 0 and statuses <= {"passed", "expected-gap", "skipped"}:
        return {"status": "passed", "exitCode": 0, "message": "All domains passed or expected-gap."}
    if "privacy-failure" in statuses or exit_code == 3:
        return {
            "status": "failed",
            "exitCode": 3 if exit_code == 3 else exit_code,
            "message": "Privacy guardrail failure (highest priority).",
        }
    if "shape-failure" in statuses or exit_code == 4:
        return {
            "status": "failed",
            "exitCode": 4 if exit_code == 4 else exit_code,
            "message": "Schema/shape failure in one or more domains.",
        }
    if "readiness-gap" in statuses or exit_code == 2:
        return {
            "status": "failed",
            "exitCode": 2 if exit_code == 2 else exit_code,
            "message": "Readiness gap in one or more domains.",
        }
    return {
        "status": "failed",
        "exitCode": exit_code,
        "message": f"Staging smoke failed (aggregated exit {exit_code}).",
    }


def build_report(
    domains: list[dict[str, Any]],
    exit_code: int,
    *,
    staging_secrets_configured: bool,
) -> dict[str, Any]:
    return {
        "version": 1,
        "generatedAt": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "stagingSecretsConfigured": staging_secrets_configured,
        "overall": overall_from_domains(domains, exit_code),
        "domains": domains,
    }


def render_markdown(report: dict[str, Any]) -> str:
    lines = [
        "# Retention staging smoke evidence",
        "",
        f"- Generated: `{report['generatedAt']}`",
        f"- Staging secrets configured: `{report['stagingSecretsConfigured']}`",
        f"- Overall: **{report['overall']['status']}** (exit `{report['overall']['exitCode']}`)",
        f"- Summary: {report['overall']['message']}",
        "",
        "| Domain | Status | Exit | Expect ready | Message |",
        "|--------|--------|------|--------------|---------|",
    ]
    for d in report["domains"]:
        expect = d["expectReady"]
        expect_str = "n/a" if expect is None else str(expect).lower()
        msg = d["message"].replace("|", "\\|")
        lines.append(
            f"| {d['domain']} | {d['status']} | {d['exitCode']} | {expect_str} | {msg} |"
        )
    lines.append("")
    lines.append(
        "_Sanitized aggregate-only summary. No API response bodies, tokens, or PII._"
    )
    return "\n".join(lines) + "\n"


def render_github_summary(report: dict[str, Any]) -> str:
    overall = report["overall"]
    lines = [
        "## Retention staging smoke",
        "",
        f"**Overall:** `{overall['status']}` (exit `{overall['exitCode']}`) — {overall['message']}",
        "",
        "| Domain | Status | Exit | Expect | Message |",
        "|--------|--------|------|--------|---------|",
    ]
    for d in report["domains"]:
        expect = d["expectReady"]
        expect_str = "n/a" if expect is None else str(expect).lower()
        msg = d["message"].replace("|", "\\|")[:120]
        lines.append(
            f"| {d['domain']} | `{d['status']}` | {d['exitCode']} | {expect_str} | {msg} |"
        )
    lines.append("")
    lines.append(
        "Artifact: `retention-staging-smoke-evidence` (sanitized JSON + Markdown only)."
    )
    return "\n".join(lines) + "\n"


def write_outputs(output_dir: Path, report: dict[str, Any]) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    json_path = output_dir / "retention-staging-smoke-results.json"
    md_path = output_dir / "retention-staging-smoke-summary.md"
    json_path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    md_path.write_text(render_markdown(report), encoding="utf-8")


def render_summary_from_json(output_dir: Path) -> None:
    json_path = output_dir / "retention-staging-smoke-results.json"
    if not json_path.is_file():
        return
    report = json.loads(json_path.read_text(encoding="utf-8"))
    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary_path:
        Path(summary_path).write_text(render_github_summary(report), encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output-dir", required=True)
    parser.add_argument("--ndjson", help="NDJSON domain run records from orchestrator")
    parser.add_argument("--skip", help="Skip all domains with this reason")
    parser.add_argument("--github-summary", action="store_true")
    parser.add_argument("--render-summary-only", action="store_true")
    parser.add_argument("--aggregated-exit", type=int, default=0)
    args = parser.parse_args()

    output_dir = Path(args.output_dir)
    domain_order = ("content", "notification", "workspace", "search")

    if args.render_summary_only:
        render_summary_from_json(output_dir)
        return 0

    if args.skip:
        domains = [skip_record(d, args.skip) for d in domain_order]
        report = build_report(domains, 0, staging_secrets_configured=False)
        write_outputs(output_dir, report)
        if args.github_summary:
            summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
            if summary_path:
                Path(summary_path).write_text(render_github_summary(report), encoding="utf-8")
        return 0

    if not args.ndjson:
        print("Either --skip or --ndjson is required", file=sys.stderr)
        return 1

    domains = []
    for line in Path(args.ndjson).read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        row = json.loads(line)
        domains.append(
            domain_record(
                row["domain"],
                int(row["exitCode"]),
                row["expectReady"] in (True, "true", "True"),
                row.get("output", ""),
            )
        )

    report = build_report(
        domains,
        args.aggregated_exit,
        staging_secrets_configured=True,
    )
    write_outputs(output_dir, report)

    if args.github_summary:
        summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
        if summary_path:
            Path(summary_path).write_text(render_github_summary(report), encoding="utf-8")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
