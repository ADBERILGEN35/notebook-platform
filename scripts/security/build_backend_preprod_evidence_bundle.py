#!/usr/bin/env python3
"""Aggregate PP-1..PP-3 staging evidence into sanitized pre-prod bundle (Faz 125)."""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

SCHEMA = "backend-preprod-evidence-bundle-v1"
BUNDLE_JSON = "backend-preprod-evidence-bundle.json"
SUMMARY_MD = "backend-preprod-evidence-summary.md"

FORBIDDEN = re.compile(
    r"Bearer [A-Za-z0-9._-]{8,}|eyJ[A-Za-z0-9_-]{10,}|\"access_token\"|"
    r"Authorization:|password|jdbc:|rawPayload|nextCursor|@odata\.nextLink|"
    r"emergency.token|\"secret\"",
    re.IGNORECASE,
)


def load_json(path: Path | None) -> tuple[dict[str, Any] | None, str | None]:
    if path is None or not path.is_file():
        return None, "missing_file"
    try:
        return json.loads(path.read_text(encoding="utf-8")), None
    except json.JSONDecodeError:
        return None, "shape_mismatch"


def privacy_scan(text: str) -> bool:
    return bool(FORBIDDEN.search(text))


def evaluate_scim(data: dict[str, Any] | None, err: str | None) -> dict[str, Any]:
    if err == "missing_file":
        return {"status": "missing", "detail": "scim-delta-sandbox-evidence.json not provided"}
    if err == "shape_mismatch" or data is None:
        return {"status": "shape_mismatch", "detail": "invalid SCIM evidence JSON"}
    if data.get("evidenceStatus") == "privacy_violation":
        return {"status": "privacy_failure", "detail": "SCIM evidence privacy violation"}
    cert = data.get("certificationResult") or ""
    provider = data.get("providerType") or data.get("provider") or "unknown"
    if cert == "certified":
        return {
            "status": "pass",
            "detail": "certificationResult=certified",
            "certificationResult": cert,
            "provider": provider,
        }
    if cert == "skipped" or data.get("evidenceStatus") == "skipped":
        return {
            "status": "missing",
            "detail": "SCIM sandbox skipped or not run",
            "certificationResult": cert or "skipped",
            "provider": provider,
        }
    return {
        "status": "fail",
        "detail": f"certificationResult={cert or 'unknown'} (certified required)",
        "certificationResult": cert or "unknown",
        "provider": provider,
    }


def evaluate_break_glass(data: dict[str, Any] | None, err: str | None) -> dict[str, Any]:
    if err == "missing_file":
        return {"status": "missing", "detail": "break-glass-revocation-evidence.json not provided"}
    if err == "shape_mismatch" or data is None:
        return {"status": "shape_mismatch", "detail": "invalid break-glass evidence JSON"}
    result = str(data.get("result") or "")
    if result == "privacy-failure":
        return {"status": "privacy_failure", "detail": "break-glass privacy violation"}
    if result == "passed":
        code = data.get("gatewayRejectErrorCode") or ""
        if code != "BREAK_GLASS_TOKEN_REVOKED":
            return {
                "status": "fail",
                "detail": f"result=passed but gatewayRejectErrorCode={code!r}",
                "result": result,
            }
        return {
            "status": "pass",
            "detail": "revocation drill passed; gateway BREAK_GLASS_TOKEN_REVOKED",
            "result": result,
            "gatewayRejectErrorCode": code,
        }
    if result == "skipped":
        return {"status": "missing", "detail": "break-glass drill skipped", "result": result}
    return {
        "status": "fail",
        "detail": f"result={result or 'unknown'}",
        "result": result,
    }


def evaluate_retention(
    data: dict[str, Any] | None, err: str | None, required: bool
) -> dict[str, Any]:
    if not required:
        return {
            "status": "not_required",
            "detail": "retentionDatasource not in production rollout scope (PP-3 waived)",
            "required": False,
        }
    if err == "missing_file":
        return {
            "status": "missing",
            "detail": "retention-staging-smoke-results.json not provided",
            "required": True,
        }
    if err == "shape_mismatch" or data is None:
        return {
            "status": "shape_mismatch",
            "detail": "invalid retention smoke JSON",
            "required": True,
        }
    overall = data.get("overall") or {}
    ostatus = str(overall.get("status") or data.get("status") or "")
    if ostatus == "privacy_violation":
        return {"status": "privacy_failure", "detail": "retention privacy violation", "required": True}
    domains = data.get("domains") or []
    passed = sum(1 for d in domains if d.get("status") == "passed")
    total = len(domains)
    if ostatus == "passed" and total > 0 and passed == total:
        return {
            "status": "pass",
            "detail": f"all {total} domains passed",
            "required": True,
            "domainsPassed": passed,
            "domainsTotal": total,
        }
    if ostatus == "skipped":
        return {"status": "missing", "detail": "retention smoke skipped", "required": True}
    return {
        "status": "fail",
        "detail": f"overall={ostatus or 'unknown'} domains={passed}/{total}",
        "required": True,
        "domainsPassed": passed,
        "domainsTotal": total,
    }


def decide_recommendation(
    pp1: dict[str, Any],
    pp2: dict[str, Any],
    pp3: dict[str, Any],
    accepted_risks: list[str],
    high_risk_count: int,
) -> tuple[str, int, list[str]]:
    missing: list[str] = []
    blockers = 0

    for label, pp in [("PP-1", pp1), ("PP-2", pp2), ("PP-3", pp3)]:
        st = pp.get("status")
        if st in ("privacy_failure", "shape_mismatch"):
            blockers += 1
            missing.append(f"{label}:{st}")
        elif st == "missing":
            blockers += 1
            missing.append(f"{label}:missing")
        elif st == "fail":
            blockers += 1
            missing.append(f"{label}:fail")
        elif st == "not_required":
            continue
        elif st != "pass":
            blockers += 1
            missing.append(f"{label}:{st}")

    if blockers > 0:
        return "NO_GO", blockers, missing

    if accepted_risks or high_risk_count > 0:
        return "GO_WITH_ACCEPTED_RISKS", 0, missing

    return "GO", 0, missing


def write_summary_md(bundle: dict[str, Any], path: Path) -> None:
    lines = [
        "# Backend pre-prod evidence bundle summary",
        "",
        f"Generated: {bundle.get('generatedAt')}",
        "",
        "> Sanitized aggregate only. No tokens, credentials, or raw API payloads.",
        "",
        "## Release candidate",
        "",
        f"| Field | Value |",
        f"|-------|--------|",
        f"| Release candidate ID | {bundle.get('releaseCandidateId')} |",
        f"| Environment | {bundle.get('environment')} |",
        f"| Git SHA | {bundle.get('gitSha')} |",
        f"| Image tag | {bundle.get('imageTag')} |",
        f"| **Final recommendation** | **{bundle.get('finalRecommendation')}** |",
        "",
        "## Pre-prod evidence (PP-1..PP-3)",
        "",
        f"| Gate | Status | Detail |",
        f"|------|--------|--------|",
    ]
    for key, title in [
        ("pp1Scim", "PP-1 SCIM delta sandbox"),
        ("pp2BreakGlass", "PP-2 Break-glass revocation"),
        ("pp3Retention", "PP-3 Retention staging smoke"),
    ]:
        pp = bundle.get(key) or {}
        lines.append(f"| {title} | {pp.get('status')} | {pp.get('detail')} |")
    lines.extend(
        [
            "",
            "## Counts",
            "",
            f"- Blockers: {bundle.get('blockerCount')}",
            f"- High risks (documented): {bundle.get('highRiskCount')}",
            f"- Missing evidence flags: {', '.join(bundle.get('missingEvidence') or []) or 'none'}",
            "",
            "## Accepted risks",
            "",
        ]
    )
    risks = bundle.get("acceptedRisks") or []
    if risks:
        for r in risks:
            lines.append(f"- {r}")
    else:
        lines.append("- none in bundle input")
    lines.extend(
        [
            "",
            "## CR handoff",
            "",
            "Attach this bundle with upstream artifacts:",
            "",
            "- `scim-delta-sandbox-evidence.json`",
            "- `break-glass-revocation-evidence.json`",
            "- `retention-staging-smoke-results.json` (if PP-3 required)",
            "",
        ]
    )
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def render_github_summary(bundle: dict[str, Any]) -> None:
    p = os.environ.get("GITHUB_STEP_SUMMARY")
    if not p:
        return
    md = f"""## Backend pre-prod evidence bundle

| Field | Value |
|-------|--------|
| Final recommendation | **{bundle.get('finalRecommendation')}** |
| PP-1 SCIM | {bundle.get('pp1Scim', {}).get('status')} |
| PP-2 Break-glass | {bundle.get('pp2BreakGlass', {}).get('status')} |
| PP-3 Retention | {bundle.get('pp3Retention', {}).get('status')} |
| Blockers | {bundle.get('blockerCount')} |

"""
    Path(p).write_text(md, encoding="utf-8")


def exit_code(bundle: dict[str, Any]) -> int:
    rec = bundle.get("finalRecommendation")
    if bundle.get("privacyViolation"):
        return 3
    if rec == "NO_GO":
        return 5
    if rec == "GO_WITH_ACCEPTED_RISKS":
        return 0
    return 0


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output-dir", required=True)
    parser.add_argument("--release-candidate-id", default="")
    parser.add_argument("--environment", default="staging")
    parser.add_argument("--scim-evidence", default="")
    parser.add_argument("--break-glass-evidence", default="")
    parser.add_argument("--retention-evidence", default="")
    parser.add_argument("--pp3-required", action="store_true")
    parser.add_argument("--accepted-risks-json", default="[]")
    parser.add_argument("--high-risk-count", type=int, default=0)
    parser.add_argument("--github-summary", action="store_true")
    args = parser.parse_args()

    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    scim_path = Path(args.scim_evidence) if args.scim_evidence else None
    bg_path = Path(args.break_glass_evidence) if args.break_glass_evidence else None
    ret_path = Path(args.retention_evidence) if args.retention_evidence else None

    scim_data, scim_err = load_json(scim_path)
    bg_data, bg_err = load_json(bg_path)
    ret_data, ret_err = load_json(ret_path)

    pp1 = evaluate_scim(scim_data, scim_err)
    pp2 = evaluate_break_glass(bg_data, bg_err)
    pp3 = evaluate_retention(ret_data, ret_err, args.pp3_required)

    try:
        accepted = json.loads(args.accepted_risks_json or "[]")
    except json.JSONDecodeError:
        accepted = []

    rec, blockers, missing_ev = decide_recommendation(
        pp1, pp2, pp3, accepted, args.high_risk_count
    )

    privacy_violation = any(
        pp.get("status") == "privacy_failure" for pp in (pp1, pp2, pp3)
    )
    shape_mismatch = any(
        pp.get("status") == "shape_mismatch" for pp in (pp1, pp2, pp3)
    )

    bundle: dict[str, Any] = {
        "evidenceSchemaVersion": SCHEMA,
        "releaseCandidateId": args.release_candidate_id
        or os.environ.get("RELEASE_CANDIDATE_ID", "unknown"),
        "gitSha": os.environ.get("GIT_SHA", "unknown"),
        "imageTag": os.environ.get("IMAGE_TAG", "unknown"),
        "environment": args.environment,
        "generatedAt": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "pp1Scim": pp1,
        "pp2BreakGlass": pp2,
        "pp3Retention": pp3,
        "blockerCount": blockers,
        "highRiskCount": args.high_risk_count,
        "acceptedRisks": accepted,
        "missingEvidence": missing_ev,
        "privacyViolation": privacy_violation,
        "shapeMismatch": shape_mismatch,
        "finalRecommendation": rec,
    }

    json_path = output_dir / BUNDLE_JSON
    json_path.write_text(json.dumps(bundle, indent=2) + "\n", encoding="utf-8")
    write_summary_md(bundle, output_dir / SUMMARY_MD)

    combined = json_path.read_text(encoding="utf-8") + (output_dir / SUMMARY_MD).read_text(
        encoding="utf-8"
    )
    if privacy_scan(combined):
        bundle["privacyViolation"] = True
        bundle["finalRecommendation"] = "NO_GO"
        bundle["blockerCount"] = max(bundle["blockerCount"], 1)
        json_path.write_text(json.dumps(bundle, indent=2) + "\n", encoding="utf-8")
        write_summary_md(bundle, output_dir / SUMMARY_MD)

    if args.github_summary:
        render_github_summary(bundle)

    code = exit_code(bundle)
    print(str(code))
    return code


if __name__ == "__main__":
    raise SystemExit(main())
