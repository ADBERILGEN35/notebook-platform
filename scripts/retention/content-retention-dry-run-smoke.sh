#!/usr/bin/env bash
#
# Content retention dry-run smoke test (Faz 101).
#
# Calls gateway /admin/retention/platform/plan and validates that:
#   - response shape is aggregate-only (no raw note/comment body, no email)
#   - DRY_RUN_READY content targets are present
#   - eligibleCount is null or non-negative integer
#   - purgeableCount is non-negative integer
#   - Faz 101 backend guardrail warnings surface cleanly
#
# Inputs (env, no secrets baked in):
#   BASE_URL       gateway base URL (default http://localhost:8080)
#   ACCESS_TOKEN   admin JWT with admin:retention:read permission (required)
#
# Exit codes:
#   0  smoke ok
#   1  request or shape error
#   2  CONTENT_RETENTION_SERVICE_UNAVAILABLE warning observed (degraded)
#   3  CONTENT_RETENTION_DB_PERMISSION_DENIED / RLS_NOT_READY observed (readiness gap)

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
: "${ACCESS_TOKEN:?ACCESS_TOKEN is required (admin JWT with admin:retention:read)}"

response_file="$(mktemp)"
trap 'rm -f "$response_file"' EXIT

echo "Calling $BASE_URL/admin/retention/platform/plan ..."
http_status="$(curl -sS -o "$response_file" -w "%{http_code}" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Accept: application/json" \
  "$BASE_URL/admin/retention/platform/plan?dryRun=true")"

if [[ "$http_status" != "200" ]]; then
  echo "Unexpected HTTP status $http_status" >&2
  cat "$response_file" >&2
  exit 1
fi

python3 - "$response_file" <<'PY'
import json
import sys

CONTENT_DRY_RUN_KEYS = {
    "content.note_versions",
    "content.comments",
    "content.search_documents",
}
FORBIDDEN_TOKENS = ("contentBlocks", "noteBody", "commentBody", "userEmail", "noteTitle")

with open(sys.argv[1], "r", encoding="utf-8") as fp:
    raw = fp.read()

try:
    body = json.loads(raw)
except json.JSONDecodeError as exc:
    print(f"Response is not valid JSON: {exc}", file=sys.stderr)
    sys.exit(1)

if not isinstance(body, dict) or "targets" not in body or "warnings" not in body:
    print("Response missing required keys 'targets' and 'warnings'", file=sys.stderr)
    sys.exit(1)

for token in FORBIDDEN_TOKENS:
    if token in raw:
        print(f"Forbidden token '{token}' present in response", file=sys.stderr)
        sys.exit(1)

targets = body.get("targets") or []
warnings = body.get("warnings") or []
seen_keys = {t.get("targetKey") for t in targets if isinstance(t, dict)}
missing = CONTENT_DRY_RUN_KEYS - seen_keys
if missing:
    print(f"Content dry-run targets missing: {sorted(missing)}", file=sys.stderr)
    sys.exit(1)

for target in targets:
    if not isinstance(target, dict):
        continue
    key = target.get("targetKey")
    if key not in CONTENT_DRY_RUN_KEYS:
        continue
    eligible = target.get("eligibleCount")
    if eligible is not None and (not isinstance(eligible, int) or eligible < 0):
        print(f"{key}: eligibleCount must be null or non-negative integer", file=sys.stderr)
        sys.exit(1)
    purgeable = target.get("purgeableCount")
    if not isinstance(purgeable, int) or purgeable < 0:
        print(f"{key}: purgeableCount must be a non-negative integer", file=sys.stderr)
        sys.exit(1)
    target_warnings = target.get("warnings") or []
    for w in target_warnings:
        if not isinstance(w, str) or not w.isascii():
            print(f"{key}: non-symbolic warning '{w}'", file=sys.stderr)
            sys.exit(1)

exit_code = 0
combined_warnings = set(warnings)
for target in targets:
    if isinstance(target, dict):
        combined_warnings.update(target.get("warnings") or [])

if "CONTENT_RETENTION_DB_PERMISSION_DENIED" in combined_warnings or \
   "CONTENT_RETENTION_RLS_NOT_READY" in combined_warnings:
    print("Readiness gap: retention DB role not configured (see runbook).", file=sys.stderr)
    exit_code = 3
elif "CONTENT_RETENTION_SERVICE_UNAVAILABLE" in combined_warnings:
    print("Degraded: content-service unavailable to gateway.", file=sys.stderr)
    exit_code = 2

print("Targets seen:", ", ".join(sorted(seen_keys)))
print("Warnings:", ", ".join(sorted(combined_warnings)) if combined_warnings else "(none)")
sys.exit(exit_code)
PY
