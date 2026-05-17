#!/usr/bin/env python3
"""Write backend RC readiness summary + JSON from check results TSV (Faz 127)."""

from __future__ import annotations

import json
import os
import sys
from datetime import datetime, timezone
from pathlib import Path

SCHEMA = "backend-rc-readiness-v1"
RESULTS_JSON = "backend-rc-readiness-results.json"
SUMMARY_MD = "backend-rc-readiness-summary.md"


def parse_tsv(path: Path) -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    if not path.is_file():
        return rows
    for line in path.read_text(encoding="utf-8").splitlines():
        parts = line.split("|", 4)
        if len(parts) < 4:
            continue
        rows.append(
            {
                "id": parts[0],
                "status": parts[1],
                "category": parts[2],
                "detail": parts[3] if len(parts) > 3 else "",
            }
        )
    return rows


def verdict_for(checks: list[dict[str, str]]) -> tuple[str, list[str]]:
    failures = [c for c in checks if c["status"] == "fail"]
    skips = [c for c in checks if c["status"] == "skipped"]
    if failures:
        cats = sorted({c["category"] for c in failures if c["category"]})
        return "FAIL", cats
    if skips:
        return "PASS_WITH_ENVIRONMENT_SKIPS", []
    return "PASS", []


def write_summary(bundle: dict[str, object], path: Path) -> None:
    lines = [
        "# Backend RC readiness summary",
        "",
        f"Generated: {bundle.get('generatedAt')}",
        "",
        "> Sanitized gate output only. No tokens, credentials, or raw API payloads.",
        "",
        f"## Verdict: **{bundle.get('verdict')}**",
        "",
        "| Check | Status | Category | Detail |",
        "|-------|--------|----------|--------|",
    ]
    for c in bundle.get("checks") or []:
        detail = str(c.get("detail", "")).replace("|", "\\|")[:120]
        lines.append(
            f"| {c.get('id')} | {c.get('status')} | {c.get('category')} | {detail} |"
        )
    lines.extend(
        [
            "",
            "## Failure categories",
            "",
        ]
    )
    fc = bundle.get("failureCategories") or []
    if fc:
        for f in fc:
            lines.append(f"- {f}")
    else:
        lines.append("- none")
    lines.extend(
        [
            "",
            "## Environment skips",
            "",
        ]
    )
    es = bundle.get("environmentSkips") or []
    if es:
        for e in es:
            lines.append(f"- {e}")
    else:
        lines.append("- none")
    lines.extend(
        [
            "",
            "## Notes",
            "",
            "- Docker / Testcontainers integration tests are **not** part of this RC gate (`docker-ci` separate).",
            "- Frontend toolchain is **not** required for backend RC readiness.",
            "- Live staging PP-1..PP-3 evidence is validated separately via pre-prod bundle workflow.",
            "",
        ]
    )
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> int:
    tsv = Path(os.environ.get("RC_RESULTS_TSV", ""))
    out_dir = Path(os.environ.get("RC_OUTPUT_DIR", "backend-rc-readiness-out"))
    out_dir.mkdir(parents=True, exist_ok=True)

    checks = parse_tsv(tsv)
    verdict, failure_cats = verdict_for(checks)
    env_skips = [
        c["id"] + (f": {c['detail']}" if c.get("detail") else "")
        for c in checks
        if c["status"] == "skipped"
    ]

    bundle: dict[str, object] = {
        "schemaVersion": SCHEMA,
        "generatedAt": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "gitSha": os.environ.get("GIT_SHA", "unknown"),
        "releaseCandidateId": os.environ.get("RELEASE_CANDIDATE_ID", ""),
        "verdict": verdict,
        "checks": checks,
        "failureCategories": failure_cats,
        "environmentSkips": env_skips,
        "dockerCiNote": "Docker/Testcontainers suites run in separate docker-ci pipeline; not a blocker for this gate.",
    }

    (out_dir / RESULTS_JSON).write_text(
        json.dumps(bundle, indent=2) + "\n", encoding="utf-8"
    )
    write_summary(bundle, out_dir / SUMMARY_MD)

    gh = os.environ.get("GITHUB_STEP_SUMMARY")
    if gh:
        Path(gh).write_text(
            f"## Backend RC readiness\n\n**Verdict:** {verdict}\n\n",
            encoding="utf-8",
        )
        with Path(gh).open("a", encoding="utf-8") as f:
            f.write("| Check | Status |\n|-------|--------|\n")
            for c in checks:
                f.write(f"| {c['id']} | {c['status']} |\n")

    print(verdict)
    return 0 if verdict != "FAIL" else 1


if __name__ == "__main__":
    raise SystemExit(main())
