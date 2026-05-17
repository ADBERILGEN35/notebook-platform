#!/usr/bin/env python3
"""Write backend Docker CI summary + JSON from check results TSV (Faz 128)."""

from __future__ import annotations

import json
import os
from datetime import datetime, timezone
from pathlib import Path

SCHEMA = "backend-docker-ci-v1"
RESULTS_JSON = "backend-docker-ci-results.json"
SUMMARY_MD = "backend-docker-ci-summary.md"


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
        return "ENVIRONMENT_SKIPPED", []
    return "PASS", []


def write_summary(bundle: dict[str, object], path: Path) -> None:
    lines = [
        "# Backend Docker CI summary",
        "",
        f"Generated: {bundle.get('generatedAt')}",
        "",
        "> Sanitized gate summary. Full test logs remain in Gradle/GitHub Actions output only.",
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
    lines.extend(["", "## Failure categories", ""])
    fc = bundle.get("failureCategories") or []
    lines.extend([f"- {f}" for f in fc] if fc else ["- none"])
    lines.extend(
        [
            "",
            "## Relation to RC gate (Faz 127)",
            "",
            "- **RC gate** (`ci-backend-rc-readiness.sh`): fast, targeted tests + fixtures; Docker not required.",
            "- **Docker CI** (this gate): full `./gradlew` verification + Testcontainers / RLS integration.",
            "",
            "## Timeout note",
            "",
            "Full `check` + `rlsIntegrationTest` may take 15–45 minutes on CI depending on cache. "
            "Increase workflow `timeout-minutes` if needed.",
            "",
        ]
    )
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> int:
    tsv = Path(os.environ.get("DOCKER_CI_RESULTS_TSV", ""))
    out_dir = Path(os.environ.get("DOCKER_CI_OUTPUT_DIR", "backend-docker-ci-out"))
    out_dir.mkdir(parents=True, exist_ok=True)

    checks = parse_tsv(tsv)
    verdict, failure_cats = verdict_for(checks)

    bundle: dict[str, object] = {
        "schemaVersion": SCHEMA,
        "generatedAt": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "gitSha": os.environ.get("GIT_SHA", "unknown"),
        "gradleTasks": os.environ.get("BACKEND_DOCKER_GRADLE_TASKS", "check rlsIntegrationTest"),
        "verdict": verdict,
        "checks": checks,
        "failureCategories": failure_cats,
        "rcGateNote": "See ci-backend-rc-readiness.sh for fast release-candidate gate.",
    }

    (out_dir / RESULTS_JSON).write_text(
        json.dumps(bundle, indent=2) + "\n", encoding="utf-8"
    )
    write_summary(bundle, out_dir / SUMMARY_MD)

    gh = os.environ.get("GITHUB_STEP_SUMMARY")
    if gh:
        md = f"## Backend Docker CI\n\n**Verdict:** {verdict}\n\n"
        Path(gh).write_text(md, encoding="utf-8")
        with Path(gh).open("a", encoding="utf-8") as f:
            f.write("| Check | Status |\n|-------|--------|\n")
            for c in checks:
                f.write(f"| {c['id']} | {c['status']} |\n")

    print(verdict)
    return 0 if verdict in ("PASS", "ENVIRONMENT_SKIPPED") else 1


if __name__ == "__main__":
    raise SystemExit(main())
