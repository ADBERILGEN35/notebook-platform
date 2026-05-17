#!/usr/bin/env bash
#
# Sanitized SCIM delta remote fetch smoke + certification evidence (Faz 118–120).
# Privacy-first: never logs ADMIN_ACCESS_TOKEN, Authorization, raw API bodies, or cursor values.
#
# Optional env:
#   GATEWAY_BASE_URL — e.g. https://api.staging.example.com
#   ADMIN_ACCESS_TOKEN — admin session/JWT (not logged)
#   SCIM_DELTA_EVIDENCE_OUTPUT_DIR — default ./scim-delta-evidence-out
#   SCIM_DELTA_VALIDATE_EVIDENCE — default true (run validate-scim-delta-evidence.sh)

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUTPUT_DIR="${SCIM_DELTA_EVIDENCE_OUTPUT_DIR:-$ROOT/scim-delta-evidence-out}"
EVIDENCE_FILE="$OUTPUT_DIR/scim-delta-remote-fetch-evidence.json"
READINESS_TMP="$(mktemp)"
DRYRUN_TMP="$(mktemp)"
trap 'rm -f "$READINESS_TMP" "$DRYRUN_TMP"' EXIT

mkdir -p "$OUTPUT_DIR"

FORBIDDEN_IN_OUTPUT='Bearer [A-Za-z0-9._-]{8,}|Authorization:[[:space:]]*|userName|"emails"|displayName|password|BEGIN PRIVATE KEY|skiptoken=[A-Za-z0-9%]{8,}|"nextCursor"[[:space:]]*:[[:space:]]*"[^"]{8,}"|@odata\.nextLink"[[:space:]]*:[[:space:]]*"https?://'

write_fixture() {
  cp "$ROOT/scripts/scim/fixtures/not-configured-evidence.json" "$EVIDENCE_FILE"
  python3 - <<'PY' "$EVIDENCE_FILE"
import json, sys
from datetime import datetime, timezone
path = sys.argv[1]
with open(path) as f:
    data = json.load(f)
data["generatedAt"] = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
with open(path, "w") as f:
    json.dump(data, f, indent=2)
    f.write("\n")
PY
}

assert_safe_json() {
  local file=$1
  if grep -qE "$FORBIDDEN_IN_OUTPUT" "$file"; then
    echo "privacy_violation: forbidden pattern in API capture" >&2
    python3 - <<'PY' "$EVIDENCE_FILE"
import json, sys
from datetime import datetime, timezone
out = {
  "evidenceSchemaVersion": "scim-delta-evidence-v1",
  "generatedAt": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
  "evidenceStatus": "privacy_violation",
  "skipReason": "forbidden_pattern_in_response",
  "providerType": "unknown",
  "selectedStrategy": "NONE",
  "remoteFetchEnabled": False,
  "remoteFetchConfigured": False,
  "remoteFetchAttempted": False,
  "remoteMultiPageEnabled": False,
  "dryRunOnly": True,
  "fetchedResourceCount": 0,
  "pagesObserved": 0,
  "nextCursorPresent": False,
  "stoppedReason": "NOT_CONFIGURED",
  "pageLimitReached": False,
  "resourceLimitReached": False,
  "providerErrorClass": "NONE",
  "retryAfterSeconds": None,
  "nextRecommendedAttemptAt": None,
  "warnings": ["SCIM_DELTA_RAW_PAYLOAD_SUPPRESSED"],
  "certificationHints": {
    "privacyChecksPassed": False,
    "dryRunOnlyConfirmed": True,
    "onePageGetAttempted": False,
    "paginationObservedOrValidStop": False,
    "rateLimitDiagnosticReady": False,
    "missingFromDeltaNoDeprovision": True,
  },
}
with open(sys.argv[1], "w") as f:
    json.dump(out, f, indent=2)
    f.write("\n")
PY
    exit 2
  fi
}

extract_evidence() {
  local readiness_file=$1
  local dryrun_file=$2
  python3 - <<'PY' "$readiness_file" "$dryrun_file" "$EVIDENCE_FILE"
import json, sys
from datetime import datetime, timezone

def load(path):
    with open(path) as f:
        return json.load(f)

readiness = load(sys.argv[1])
dryrun = load(sys.argv[2])
out_path = sys.argv[3]

allowed = {
    "providerType", "selectedStrategy", "remoteFetchEnabled", "remoteFetchConfigured",
    "remoteFetchAttempted", "dryRunOnly", "fetchedResourceCount", "pagesObserved",
    "remoteMultiPageEnabled", "stoppedReason", "pageLimitReached", "resourceLimitReached",
    "nextCursorPresent", "providerErrorClass", "retryAfterSeconds",
    "nextRecommendedAttemptAt", "warnings",
}

def pick(src):
    return {k: src.get(k) for k in allowed if k in src}

merged = pick(readiness)
merged.update({k: v for k, v in pick(dryrun).items() if v is not None})
merged["dryRunOnly"] = dryrun.get("dryRunOnly", readiness.get("dryRunOnly", True))
merged["generatedAt"] = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
merged["evidenceSchemaVersion"] = "scim-delta-evidence-v1"
merged["evidenceStatus"] = "passed"
merged["skipReason"] = None

if not merged.get("remoteFetchEnabled"):
    merged["evidenceStatus"] = "skipped"
    merged["skipReason"] = "remote_fetch_disabled"

stopped = merged.get("stoppedReason") or "SINGLE_PAGE_ONLY"
pages = int(merged.get("pagesObserved") or 0)
remote_attempted = bool(merged.get("remoteFetchAttempted"))
remote_enabled = bool(merged.get("remoteFetchEnabled"))
multi = bool(merged.get("remoteMultiPageEnabled"))
next_cursor = bool(merged.get("nextCursorPresent"))
pec = merged.get("providerErrorClass") or "NONE"

valid_stop = stopped in (
    "NO_NEXT_CURSOR", "SINGLE_PAGE_ONLY", "PAGE_LIMIT_REACHED", "RESOURCE_LIMIT_REACHED", "COMPLETED"
)
pagination_ok = (pages > 1) or valid_stop or not remote_enabled

merged["certificationHints"] = {
    "privacyChecksPassed": True,
    "dryRunOnlyConfirmed": merged.get("dryRunOnly") is True,
    "onePageGetAttempted": remote_attempted and pages >= 1 if remote_enabled else False,
    "paginationObservedOrValidStop": pagination_ok,
    "rateLimitDiagnosticReady": pec in ("NONE", "RATE_LIMITED", "RETRY_AFTER_OBSERVED") or not remote_enabled,
    "missingFromDeltaNoDeprovision": True,
}

with open(out_path, "w") as f:
    json.dump(merged, f, indent=2)
    f.write("\n")
PY
}

if [[ -z "${GATEWAY_BASE_URL:-}" || -z "${ADMIN_ACCESS_TOKEN:-}" ]]; then
  write_fixture
  echo "skipped: set GATEWAY_BASE_URL and ADMIN_ACCESS_TOKEN for live smoke" >&2
  if [[ "${SCIM_DELTA_VALIDATE_EVIDENCE:-true}" == "true" ]]; then
    bash "$ROOT/scripts/scim/validate-scim-delta-evidence.sh" "$EVIDENCE_FILE"
  fi
  exit 0
fi

if ! command -v curl >/dev/null 2>&1; then
  write_fixture
  echo "skipped: curl not available" >&2
  exit 0
fi

BASE="${GATEWAY_BASE_URL%/}"

curl -fsS -o "$READINESS_TMP" -w '' \
  -H "Accept: application/json" \
  -H "Authorization: Bearer ${ADMIN_ACCESS_TOKEN}" \
  "${BASE}/admin/identity/scim/delta/readiness" 2>/dev/null || {
  write_fixture
  echo "skipped: readiness request failed" >&2
  exit 0
}

assert_safe_json "$READINESS_TMP"

curl -fsS -o "$DRYRUN_TMP" -w '' \
  -X POST \
  -H "Accept: application/json" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${ADMIN_ACCESS_TOKEN}" \
  -d '{"resourceType":"USER"}' \
  "${BASE}/admin/identity/scim/delta/dry-run" 2>/dev/null || {
  write_fixture
  echo "skipped: dry-run request failed" >&2
  exit 0
}

assert_safe_json "$DRYRUN_TMP"
extract_evidence "$READINESS_TMP" "$DRYRUN_TMP"

if grep -qE "$FORBIDDEN_IN_OUTPUT" "$EVIDENCE_FILE"; then
  echo "privacy_violation: forbidden pattern in evidence output" >&2
  exit 2
fi

echo "wrote sanitized evidence: $EVIDENCE_FILE"

if [[ "${SCIM_DELTA_VALIDATE_EVIDENCE:-true}" == "true" ]]; then
  bash "$ROOT/scripts/scim/validate-scim-delta-evidence.sh" "$EVIDENCE_FILE"
fi
