#!/usr/bin/env python3
"""Write frontend RC readiness summary + JSON from check results TSV (Faz 146)."""

from __future__ import annotations

import json
import os
import re
import sys
from datetime import datetime, timezone
from pathlib import Path

SCHEMA = "frontend-rc-readiness-v1"
RESULTS_JSON = "frontend-rc-readiness-results.json"
SUMMARY_MD = "frontend-rc-readiness-summary.md"
ROUTER_FILE = "frontend/src/app/router.tsx"

AUTH_PREFIXES = ("/login", "/register", "/signup", "/forgot-password", "/mfa", "/sso/callback")
WORKSPACE_MARKERS = ("workspaces", "notebooks", "notes/")
SEARCH_SETTINGS_MARKERS = ("search", "settings")
ADMIN_MARKER = "/admin"


def parse_tsv(path: Path) -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    if not path.is_file():
        return rows
    for line in path.read_text(encoding="utf-8").splitlines():
        parts = line.split("|", 3)
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


def collect_routes(router_path: Path) -> list[str]:
    if not router_path.is_file():
        return []
    text = router_path.read_text(encoding="utf-8")
    routes: set[str] = set()
    for m in re.finditer(r"path:\s*['\"]([^'\"]+)['\"]", text):
        p = m.group(1).strip()
        if not p:
            continue
        if p.startswith("/"):
            routes.add(p)
        else:
            routes.add(f"/app/{p}")
    routes.add("/app")
    return sorted(routes)


def categorize_routes(routes: list[str]) -> dict[str, list[str]]:
    auth: list[str] = []
    app: list[str] = []
    workspace: list[str] = []
    search_settings: list[str] = []
    admin: list[str] = []

    for r in routes:
        if any(r == p or r.startswith(p + "/") for p in AUTH_PREFIXES if p != "/"):
            auth.append(r)
        elif ADMIN_MARKER in r:
            admin.append(r)
        elif any(x in r for x in ("workspaces", "notebooks", "/notes/")):
            workspace.append(r)
        elif any(x in r for x in SEARCH_SETTINGS_MARKERS):
            search_settings.append(r)
        elif r.startswith("/app"):
            app.append(r)
        elif r.startswith("/"):
            auth.append(r)

    return {
        "auth": sorted(set(auth)),
        "app": sorted(set(app)),
        "workspace": sorted(set(workspace)),
        "searchSettings": sorted(set(search_settings)),
        "admin": sorted(set(admin)),
    }


def smoke_summary(checks: list[dict[str, str]]) -> dict[str, str]:
    pw = next((c for c in checks if c["id"] == "playwright-smoke"), None)
    if not pw:
        return {"playwright": "not-run"}
    if pw["status"] == "pass":
        return {"playwright": "pass", "scope": "tests/e2e (chromium)"}
    if pw["status"] == "skipped":
        return {"playwright": "environment-skipped", "detail": pw.get("detail", "")}
    return {"playwright": "fail", "category": pw.get("category", "")}


def write_summary(bundle: dict[str, object], path: Path) -> None:
    lines = [
        "# Frontend RC readiness summary",
        "",
        f"Generated: {bundle.get('generatedAt')}",
        "",
        "> Sanitized gate output only. No tokens, credentials, JWT, or raw API payloads.",
        "",
        f"## Verdict: **{bundle.get('verdict')}**",
        "",
        "## Checks",
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
    if fc:
        for f in fc:
            lines.append(f"- {f}")
    else:
        lines.append("- none")

    lines.extend(["", "## Environment skips", ""])
    es = bundle.get("environmentSkips") or []
    if es:
        for e in es:
            lines.append(f"- {e}")
    else:
        lines.append("- none")

    inv = bundle.get("routeInventory") or {}
    lines.extend(["", "## Route inventory (smoke scope)", ""])
    for section, key in [
        ("Auth", "auth"),
        ("App shell", "app"),
        ("Workspace / notes", "workspace"),
        ("Search / settings", "searchSettings"),
        ("Admin", "admin"),
    ]:
        lines.append(f"")
        lines.append(f"### {section}")
        routes = inv.get(key) or []
        if routes:
            for r in routes[:40]:
                lines.append(f"- `{r}`")
            if len(routes) > 40:
                lines.append(f"- … and {len(routes) - 40} more")
        else:
            lines.append("- (none)")

    smoke = bundle.get("smokeSummary") or {}
    lines.extend(
        [
            "",
            "## E2E smoke summary",
            "",
            f"- Playwright: **{smoke.get('playwright', 'unknown')}**",
        ]
    )
    if smoke.get("scope"):
        lines.append(f"- Scope: {smoke.get('scope')}")
    if smoke.get("detail"):
        lines.append(f"- Skip reason: {smoke.get('detail')}")

    lines.extend(
        [
            "",
            "## Notes",
            "",
            "- Backend RC gate is separate (`backend-rc-readiness.yml`).",
            "- Production feature flags are **not** enabled by this gate.",
            "- Legacy `frontend/e2e/` suite is not part of this RC gate (see `tests/e2e` smoke).",
            "",
        ]
    )
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> int:
    root = Path(os.environ.get("RC_ROOT", ".")).resolve()
    tsv = Path(os.environ.get("RC_RESULTS_TSV", ""))
    out_dir = Path(os.environ.get("RC_OUTPUT_DIR", "frontend-rc-readiness-out"))
    out_dir.mkdir(parents=True, exist_ok=True)

    checks = parse_tsv(tsv)
    verdict, failure_cats = verdict_for(checks)
    env_skips = [
        c["id"] + (f": {c['detail']}" if c.get("detail") else "")
        for c in checks
        if c["status"] == "skipped"
    ]

    router_path = root / ROUTER_FILE
    all_routes = collect_routes(router_path)
    route_inventory = categorize_routes(all_routes)

    bundle: dict[str, object] = {
        "schemaVersion": SCHEMA,
        "generatedAt": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "gitSha": os.environ.get("GIT_SHA", "unknown"),
        "releaseCandidateId": os.environ.get("RELEASE_CANDIDATE_ID", ""),
        "verdict": verdict,
        "checks": checks,
        "failureCategories": failure_cats,
        "environmentSkips": env_skips,
        "routeInventory": route_inventory,
        "routeCount": len(all_routes),
        "smokeSummary": smoke_summary(checks),
    }

    (out_dir / RESULTS_JSON).write_text(
        json.dumps(bundle, indent=2) + "\n", encoding="utf-8"
    )
    write_summary(bundle, out_dir / SUMMARY_MD)

    gh = os.environ.get("GITHUB_STEP_SUMMARY")
    if gh:
        Path(gh).write_text(
            f"## Frontend RC readiness\n\n**Verdict:** {verdict}\n\n",
            encoding="utf-8",
        )
        with Path(gh).open("a", encoding="utf-8") as f:
            f.write("| Check | Status | Category |\n|-------|--------|----------|\n")
            for c in checks:
                f.write(f"| {c['id']} | {c['status']} | {c.get('category', '')} |\n")
            f.write("\n### Route counts\n\n")
            for key, label in [
                ("auth", "Auth"),
                ("app", "App"),
                ("workspace", "Workspace"),
                ("searchSettings", "Search/Settings"),
                ("admin", "Admin"),
            ]:
                n = len(route_inventory.get(key) or [])
                f.write(f"- {label}: {n}\n")

    print(verdict)
    return 0 if verdict != "FAIL" else 1


if __name__ == "__main__":
    sys.exit(main())
