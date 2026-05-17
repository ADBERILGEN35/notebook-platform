#!/usr/bin/env bash
#
# Workspace retention dry-run smoke test (Faz 107).
#
# Calls gateway GET /admin/retention/platform/plan and validates:
#   - workspace.invitations_expired and workspace.audit_like_events targets
#   - DRY_RUN_READY status when integration is enabled
#   - aggregate-only shape (no workspace name, user/invitation email)
#   - serviceSummaries includes workspace-service
#
# Inputs (env):
#   API_BASE_URL                     gateway base (fallback BASE_URL; default http://localhost:8080)
#   ADMIN_ACCESS_TOKEN               admin JWT with admin:retention:read (fallback ACCESS_TOKEN)
#   EXPECT_WORKSPACE_RETENTION_READY   true|false (default false)
#   RETENTION_SMOKE_FIXTURE_FILE       optional path to JSON fixture (skips curl; for tests)
#
# Exit codes:
#   0  smoke ok or expected pre-rollout gap when EXPECT_*=false
#   2  readiness gap when EXPECT_*=true
#   3  privacy guardrail violation
#   4  schema/shape mismatch

set -euo pipefail

API_BASE_URL="${API_BASE_URL:-${BASE_URL:-http://localhost:8080}}"
ADMIN_ACCESS_TOKEN="${ADMIN_ACCESS_TOKEN:-${ACCESS_TOKEN:-}}"
: "${ADMIN_ACCESS_TOKEN:?ADMIN_ACCESS_TOKEN is required (admin JWT with admin:retention:read)}"
EXPECT_READY="${EXPECT_WORKSPACE_RETENTION_READY:-false}"

response_file="$(mktemp)"
trap 'rm -f "$response_file"' EXIT

if [[ -n "${RETENTION_SMOKE_FIXTURE_FILE:-}" ]]; then
  cp "$RETENTION_SMOKE_FIXTURE_FILE" "$response_file"
  http_status="200"
  echo "Using fixture $RETENTION_SMOKE_FIXTURE_FILE (no live gateway call)"
else
  echo "Calling $API_BASE_URL/admin/retention/platform/plan ..."
  http_status="$(curl -sS -o "$response_file" -w "%{http_code}" \
    -H "Authorization: Bearer $ADMIN_ACCESS_TOKEN" \
    -H "Accept: application/json" \
    "$API_BASE_URL/admin/retention/platform/plan?dryRun=true")"
fi

if [[ "$http_status" != "200" ]]; then
  echo "Unexpected HTTP status $http_status (schema/contract failure; response body withheld)" >&2
  exit 4
fi

EXPECT_READY="$EXPECT_READY" python3 - "$response_file" <<'PY'
import json
import os
import sys

WORKSPACE_DRY_RUN_KEYS = {
    "workspace.invitations_expired",
    "workspace.audit_like_events",
}
FORBIDDEN_TOKENS = (
    "workspaceName",
    "userEmail",
    "invitationEmail",
    "inviteeEmail",
    "inviteEmail",
    "tokenHash",
)
READINESS_GAP_WARNINGS = {
    "WORKSPACE_RETENTION_SERVICE_UNAVAILABLE",
    "WORKSPACE_RETENTION_DRY_RUN_DISABLED",
    "WORKSPACE_RETENTION_DB_PERMISSION_DENIED",
    "WORKSPACE_RETENTION_RLS_NOT_READY",
}

expect_ready = os.environ.get("EXPECT_READY", "false").strip().lower() == "true"

with open(sys.argv[1], "r", encoding="utf-8") as fp:
    raw = fp.read()

for token in FORBIDDEN_TOKENS:
    if token in raw:
        print(f"Privacy guardrail violation: forbidden token '{token}' in response", file=sys.stderr)
        sys.exit(3)

try:
    body = json.loads(raw)
except json.JSONDecodeError as exc:
    print(f"Response is not valid JSON: {exc}", file=sys.stderr)
    sys.exit(4)

if not isinstance(body, dict) or "targets" not in body or "warnings" not in body:
    print("Response missing required keys 'targets' and 'warnings'", file=sys.stderr)
    sys.exit(4)

targets = body.get("targets") or []
warnings = body.get("warnings") or []
summaries = body.get("serviceSummaries") or []
combined_warnings = set(w for w in warnings if isinstance(w, str))
workspace_targets = []
for target in targets:
    if not isinstance(target, dict):
        continue
    if target.get("targetKey") in WORKSPACE_DRY_RUN_KEYS:
        workspace_targets.append(target)
    combined_warnings.update(
        w for w in (target.get("warnings") or []) if isinstance(w, str)
    )

for target in workspace_targets:
    key = target.get("targetKey")
    if not isinstance(target.get("status"), str):
        print(f"{key}: status must be a string", file=sys.stderr)
        sys.exit(4)
    eligible = target.get("eligibleCount")
    if eligible is not None and (not isinstance(eligible, int) or isinstance(eligible, bool) or eligible < 0):
        print(f"{key}: eligibleCount must be null or non-negative integer", file=sys.stderr)
        sys.exit(4)
    purgeable = target.get("purgeableCount")
    if not isinstance(purgeable, int) or isinstance(purgeable, bool) or purgeable < 0:
        print(f"{key}: purgeableCount must be a non-negative integer", file=sys.stderr)
        sys.exit(4)
    if "blockedByLegalHold" not in target or not isinstance(target["blockedByLegalHold"], bool):
        print(f"{key}: blockedByLegalHold must be present and boolean", file=sys.stderr)
        sys.exit(4)

seen_keys = {t.get("targetKey") for t in workspace_targets}
missing = WORKSPACE_DRY_RUN_KEYS - seen_keys
readiness_gap = bool(READINESS_GAP_WARNINGS & combined_warnings)

summary_services = {
    s.get("service")
    for s in summaries
    if isinstance(s, dict) and isinstance(s.get("service"), str)
}

print("Workspace targets seen:", ", ".join(sorted(seen_keys)) or "(none)")
print("Warnings:", ", ".join(sorted(combined_warnings)) if combined_warnings else "(none)")
print("serviceSummaries services:", ", ".join(sorted(summary_services)) or "(none)")

if expect_ready:
    if readiness_gap:
        print(
            "Readiness gap: workspace retention not production-ready "
            "(see workspace-retention-rls-production-runbook.md).",
            file=sys.stderr,
        )
        sys.exit(2)
    if missing:
        print(f"Workspace dry-run targets missing: {sorted(missing)}", file=sys.stderr)
        sys.exit(4)
    if "workspace-service" not in summary_services:
        print("serviceSummaries missing workspace-service", file=sys.stderr)
        sys.exit(4)
    for target in workspace_targets:
        if target.get("status") != "DRY_RUN_READY":
            print(
                f"{target.get('targetKey')}: status {target.get('status')} is not DRY_RUN_READY",
                file=sys.stderr,
            )
            sys.exit(4)
    print("Workspace retention dry-run is production-ready.")
    sys.exit(0)

if readiness_gap or missing:
    print(
        "Workspace retention not enabled/available (expected pre-rollout state; "
        "identity/content/search plan unaffected)."
    )
sys.exit(0)
PY
