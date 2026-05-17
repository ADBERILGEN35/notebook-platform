#!/usr/bin/env bash
#
# Validate sanitized SCIM delta sandbox evidence JSON (Faz 120).
# Exit 0 = valid, 1 = validation failed, 2 = privacy violation (highest priority).

set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "usage: $0 <path-to-scim-delta-remote-fetch-evidence.json>" >&2
  exit 1
fi

EVIDENCE_FILE="$1"
if [[ ! -f "$EVIDENCE_FILE" ]]; then
  echo "file not found: $EVIDENCE_FILE" >&2
  exit 1
fi

FORBIDDEN_CONTENT='Bearer [A-Za-z0-9._-]{8,}|Authorization:[[:space:]]*[A-Za-z0-9]|"userName"|"emails"|"displayName"|"Resources"[[:space:]]*:\[[^]]+\{[^}]+userName|skiptoken=[A-Za-z0-9%]{8,}|"nextCursor"[[:space:]]*:[[:space:]]*"[^"]{8,}"|@odata\.nextLink"[[:space:]]*:[[:space:]]*"https?://|BEGIN PRIVATE KEY|password[[:space:]]*:[[:space:]]*"[A-Za-z0-9+/=]{12,}"'

if grep -qE "$FORBIDDEN_CONTENT" "$EVIDENCE_FILE"; then
  echo "privacy_violation: forbidden pattern in evidence file" >&2
  exit 2
fi

python3 - <<'PY' "$EVIDENCE_FILE"
import json, sys

path = sys.argv[1]
with open(path) as f:
    data = json.load(f)

required = [
    "evidenceSchemaVersion",
    "generatedAt",
    "evidenceStatus",
    "providerType",
    "selectedStrategy",
    "remoteFetchEnabled",
    "remoteFetchConfigured",
    "remoteFetchAttempted",
    "remoteMultiPageEnabled",
    "dryRunOnly",
    "pagesObserved",
    "fetchedResourceCount",
    "nextCursorPresent",
    "stoppedReason",
    "pageLimitReached",
    "resourceLimitReached",
    "providerErrorClass",
    "warnings",
    "certificationHints",
]

forbidden_keys = {
    "rawPayload",
    "rawResponse",
    "bearerToken",
    "authorization",
    "authorizationHeader",
    "nextCursor",
    "nextLink",
    "skiptoken",
    "accessToken",
    "scimBody",
}

errors = []
for key in required:
    if key not in data:
        errors.append(f"missing required field: {key}")

for key in forbidden_keys:
    if key in data:
        errors.append(f"forbidden field present: {key}")

status = data.get("evidenceStatus")
if status not in ("passed", "skipped", "failed", "privacy_violation"):
    errors.append(f"invalid evidenceStatus: {status}")

if data.get("evidenceSchemaVersion") != "scim-delta-evidence-v1":
    errors.append("evidenceSchemaVersion must be scim-delta-evidence-v1")

if status == "passed" and data.get("dryRunOnly") is not True:
    errors.append("dryRunOnly must be true for passed evidence")

hints = data.get("certificationHints")
if not isinstance(hints, dict):
    errors.append("certificationHints must be an object")
else:
    for hk in (
        "privacyChecksPassed",
        "dryRunOnlyConfirmed",
        "onePageGetAttempted",
        "paginationObservedOrValidStop",
        "rateLimitDiagnosticReady",
        "missingFromDeltaNoDeprovision",
    ):
        if hk not in hints:
            errors.append(f"certificationHints missing {hk}")

if errors:
    for e in errors:
        print(e, file=sys.stderr)
    sys.exit(1)

print("evidence validation passed")
PY
