#!/usr/bin/env bash
#
# Content retention dry-run smoke test (Faz 101 / Faz 108 exit-code alignment).
#
# Calls gateway GET /admin/retention/platform/plan and validates aggregate-only shape.
#
# Inputs (env):
#   API_BASE_URL                     gateway base (fallback BASE_URL; default http://localhost:8080)
#   ADMIN_ACCESS_TOKEN               admin JWT (fallback ACCESS_TOKEN; required unless fixture)
#   EXPECT_CONTENT_RETENTION_READY   true|false (default false)
#   RETENTION_SMOKE_FIXTURE_FILE     optional JSON fixture (skips curl)
#
# Exit codes:
#   0  smoke ok or expected pre-rollout gap when EXPECT_*=false
#   2  readiness gap when EXPECT_*=true
#   3  privacy guardrail violation
#   4  schema/shape mismatch

set -euo pipefail

API_BASE_URL="${API_BASE_URL:-${BASE_URL:-http://localhost:8080}}"
ADMIN_ACCESS_TOKEN="${ADMIN_ACCESS_TOKEN:-${ACCESS_TOKEN:-}}"
EXPECT_READY="${EXPECT_CONTENT_RETENTION_READY:-false}"

response_file="$(mktemp)"
trap 'rm -f "$response_file"' EXIT

if [[ -n "${RETENTION_SMOKE_FIXTURE_FILE:-}" ]]; then
  cp "$RETENTION_SMOKE_FIXTURE_FILE" "$response_file"
  http_status="200"
  echo "Using fixture $RETENTION_SMOKE_FIXTURE_FILE (no live gateway call)"
else
  : "${ADMIN_ACCESS_TOKEN:?ADMIN_ACCESS_TOKEN is required (admin JWT with admin:retention:read)}"
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

CONTENT_DRY_RUN_KEYS = {
    "content.note_versions",
    "content.comments",
    "content.search_documents",
}
FORBIDDEN_TOKENS = (
    "contentBlocks",
    "noteBody",
    "commentBody",
    "userEmail",
    "noteTitle",
)
READINESS_GAP_WARNINGS = {
    "CONTENT_RETENTION_SERVICE_UNAVAILABLE",
    "CONTENT_RETENTION_DRY_RUN_DISABLED",
    "CONTENT_RETENTION_DB_PERMISSION_DENIED",
    "CONTENT_RETENTION_RLS_NOT_READY",
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
combined_warnings = set(w for w in warnings if isinstance(w, str))
content_targets = []
for target in targets:
    if not isinstance(target, dict):
        continue
    if target.get("targetKey") in CONTENT_DRY_RUN_KEYS:
        content_targets.append(target)
    combined_warnings.update(
        w for w in (target.get("warnings") or []) if isinstance(w, str)
    )

for target in content_targets:
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

seen_keys = {t.get("targetKey") for t in content_targets}
missing = CONTENT_DRY_RUN_KEYS - seen_keys
readiness_gap = bool(READINESS_GAP_WARNINGS & combined_warnings)

print("Content targets seen:", ", ".join(sorted(seen_keys)) or "(none)")
print("Warnings:", ", ".join(sorted(combined_warnings)) if combined_warnings else "(none)")

if expect_ready:
    if readiness_gap:
        print(
            "Readiness gap: content retention not production-ready "
            "(see retention-rls-production-runbook.md).",
            file=sys.stderr,
        )
        sys.exit(2)
    if missing:
        print(f"Content dry-run targets missing: {sorted(missing)}", file=sys.stderr)
        sys.exit(4)
    for target in content_targets:
        if target.get("status") != "DRY_RUN_READY":
            print(
                f"{target.get('targetKey')}: status {target.get('status')} is not DRY_RUN_READY",
                file=sys.stderr,
            )
            sys.exit(4)
    print("Content retention dry-run is production-ready.")
    sys.exit(0)

if readiness_gap or missing:
    print(
        "Content retention not enabled/available (expected pre-rollout state; "
        "identity/notification/workspace/search plan unaffected)."
    )
sys.exit(0)
PY
