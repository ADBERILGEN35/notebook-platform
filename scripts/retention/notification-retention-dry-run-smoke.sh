#!/usr/bin/env bash
#
# Notification retention dry-run smoke test (Faz 103).
#
# Calls gateway /admin/retention/platform/plan and validates that:
#   - response shape is aggregate-only (no raw payload/body/recipient/email)
#   - the six notification dry-run targets are present and well-shaped
#   - eligibleCount is null or non-negative integer; purgeableCount >= 0
#   - blockedByLegalHold is present
#   - Faz 102/103 readiness warnings surface cleanly
#
# Inputs (env, no secrets baked in):
#   API_BASE_URL                       gateway base URL
#                                      (fallback BASE_URL; default http://localhost:8080)
#   ADMIN_ACCESS_TOKEN                 admin JWT with admin:retention:read
#                                      (fallback ACCESS_TOKEN; required)
#   EXPECT_NOTIFICATION_RETENTION_READY  true|false (default false)
#                                      false -> unavailable/disabled is the expected
#                                               pre-rollout/prod state (exit 0, reported)
#                                      true  -> readiness gap fails (exit 2)
#
# Exit codes (Faz 103 scheme):
#   0  smoke ok
#   2  service unavailable / DB permission denied / RLS not ready
#      readiness gap (only a failure when EXPECT_..._READY=true)
#   3  privacy guardrail violation (forbidden token in response)
#   4  schema/shape mismatch (bad HTTP, invalid JSON, missing keys/types)

set -euo pipefail

API_BASE_URL="${API_BASE_URL:-${BASE_URL:-http://localhost:8080}}"
ADMIN_ACCESS_TOKEN="${ADMIN_ACCESS_TOKEN:-${ACCESS_TOKEN:-}}"
: "${ADMIN_ACCESS_TOKEN:?ADMIN_ACCESS_TOKEN is required (admin JWT with admin:retention:read)}"
EXPECT_READY="${EXPECT_NOTIFICATION_RETENTION_READY:-false}"

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

NOTIFICATION_DRY_RUN_KEYS = {
    "notification.analytics_hourly",
    "notification.fanout_outbox_sent",
    "notification.fanout_outbox_dead",
    "notification.dead_letter_requeue_requests",
    "notification.digest_items_terminal",
    "notification.email_notifications_terminal",
}
# Precise camelCase tokens that would only appear if raw PII leaked into the
# aggregate response. Deliberately not bare "email"/"body" to avoid matching
# the legitimate `notification.email_notifications_terminal` target key.
FORBIDDEN_TOKENS = (
    "payload",
    "recipientEmail",
    "recipient",
    "subject",
    "userEmail",
    "userId",
    "workspaceId",
    "notificationBody",
    "emailBody",
)
READINESS_GAP_WARNINGS = {
    "NOTIFICATION_RETENTION_SERVICE_UNAVAILABLE",
    "NOTIFICATION_RETENTION_DB_PERMISSION_DENIED",
    "NOTIFICATION_RETENTION_RLS_NOT_READY",
}

expect_ready = os.environ.get("EXPECT_READY", "false").strip().lower() == "true"

with open(sys.argv[1], "r", encoding="utf-8") as fp:
    raw = fp.read()

# Privacy guardrail has highest priority: scan raw text before parsing so a
# leak is never masked by a parse/shape failure.
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
notif_targets = []
for target in targets:
    if not isinstance(target, dict):
        continue
    if target.get("targetKey") in NOTIFICATION_DRY_RUN_KEYS:
        notif_targets.append(target)
    combined_warnings.update(
        w for w in (target.get("warnings") or []) if isinstance(w, str)
    )

# Shape validation only for notification targets that are present (a disabled /
# unavailable integration legitimately omits them).
for target in notif_targets:
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
    for w in target.get("warnings") or []:
        if not isinstance(w, str) or not w.isascii():
            print(f"{key}: non-symbolic warning '{w}'", file=sys.stderr)
            sys.exit(4)

seen_keys = {t.get("targetKey") for t in notif_targets}
missing = NOTIFICATION_DRY_RUN_KEYS - seen_keys
readiness_gap = bool(READINESS_GAP_WARNINGS & combined_warnings)

print("Notification targets seen:", ", ".join(sorted(seen_keys)) or "(none)")
print("Warnings:", ", ".join(sorted(combined_warnings)) if combined_warnings else "(none)")

if expect_ready:
    if readiness_gap:
        print(
            "Readiness gap: notification retention not production-ready "
            "(see notification-retention-rls-production-runbook.md).",
            file=sys.stderr,
        )
        sys.exit(2)
    if missing:
        print(f"Notification dry-run targets missing: {sorted(missing)}", file=sys.stderr)
        sys.exit(4)
    for target in notif_targets:
        if target.get("status") != "DRY_RUN_READY":
            print(
                f"{target.get('targetKey')}: status {target.get('status')} "
                "is not DRY_RUN_READY",
                file=sys.stderr,
            )
            sys.exit(4)
    print("Notification retention dry-run is production-ready.")
    sys.exit(0)

# EXPECT_NOTIFICATION_RETENTION_READY=false: unavailable/disabled is the
# expected pre-rollout / production-default state.
if readiness_gap or missing:
    print(
        "Notification retention not enabled/available (expected pre-rollout "
        "state; identity/content plan unaffected)."
    )
sys.exit(0)
PY
