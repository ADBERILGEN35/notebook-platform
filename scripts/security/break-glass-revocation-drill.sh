#!/usr/bin/env bash
#
# Staging break-glass revocation drill (Faz 123).
# See scripts/security/break-glass-revocation-evidence-formats.md

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

OUTPUT_DIR="${BREAK_GLASS_REVOCATION_DRILL_OUTPUT_DIR:-$ROOT/break-glass-revocation-evidence-out}"
ENVIRONMENT="${BREAK_GLASS_STAGING_ENVIRONMENT:-staging}"
BASE_URL="${BREAK_GLASS_STAGING_API_BASE_URL:-}"
ADMIN_TOKEN="${BREAK_GLASS_STAGING_ADMIN_ACCESS_TOKEN:-}"
EMERGENCY_TOKEN="${BREAK_GLASS_STAGING_EMERGENCY_TOKEN:-}"
BG_TOKEN_IN="${BREAK_GLASS_STAGING_BREAK_GLASS_TOKEN:-}"
TEST_PATH="${BREAK_GLASS_STAGING_TEST_ENDPOINT:-/admin/enterprise/status}"
CACHE_WAIT="${BREAK_GLASS_STAGING_DENYLIST_CACHE_WAIT_SECONDS:-35}"
FIXTURE="${BREAK_GLASS_REVOCATION_DRILL_FIXTURE:-}"
GIT_SHA="${GIT_SHA:-unknown}"
IMAGE_TAG="${IMAGE_TAG:-unknown}"

HTTP_PY="$ROOT/scripts/security/break-glass-revocation-drill-http.py"
REPORT_PY="$ROOT/scripts/security/write-break-glass-revocation-report.py"
VALIDATOR="$ROOT/scripts/security/validate-break-glass-revocation-evidence.sh"

mkdir -p "$OUTPUT_DIR"
TOKEN_FILE="$(mktemp)"
chmod 600 "$TOKEN_FILE"
trap 'rm -f "$TOKEN_FILE"' EXIT

write_skip() {
  python3 "$REPORT_PY" --output-dir "$OUTPUT_DIR" --environment "$ENVIRONMENT" --skip "$1" --github-summary >/dev/null
  exit 0
}

apply_fixture() {
  local fixture="$ROOT/scripts/security/fixtures/${1}-evidence.json"
  [[ -f "$fixture" ]] || { echo "missing fixture $fixture" >&2; exit 4; }
  cp -f "$fixture" "$OUTPUT_DIR/break-glass-revocation-evidence.json"
  python3 "$REPORT_PY" --output-dir "$OUTPUT_DIR" --environment "$ENVIRONMENT" \
    --evidence-json "$(cat "$OUTPUT_DIR/break-glass-revocation-evidence.json")" --github-summary >/dev/null
  bash "$VALIDATOR" "$OUTPUT_DIR/break-glass-revocation-evidence.json"
  RESULT="$(python3 -c "import json; print(json.load(open('$OUTPUT_DIR/break-glass-revocation-evidence.json'))['result'])")"
  case "$RESULT" in
    skipped|passed) exit 0 ;;
    privacy-failure) exit 3 ;;
    readiness-gap) exit 2 ;;
    shape-mismatch) exit 4 ;;
    failed) exit 5 ;;
    *) exit 4 ;;
  esac
}

[[ -n "$FIXTURE" ]] && apply_fixture "$FIXTURE"

[[ -z "$BASE_URL" ]] && { echo "skip: no API base URL"; write_skip "missing_staging_api_base_url"; }
[[ -z "$ADMIN_TOKEN" ]] && { echo "skip: no admin token"; write_skip "missing_staging_admin_credential"; }
[[ -z "$BG_TOKEN_IN" && -z "$EMERGENCY_TOKEN" ]] && { echo "skip: no break-glass credentials"; write_skip "missing_staging_break_glass_credential"; }

REVOKE_REASON="Staging break-glass revocation drill evidence run $(date -u +%Y-%m-%dT%H:%M:%SZ)"
LOGIN_REASON="Staging break-glass revocation drill login $(date -u +%Y-%m-%dT%H:%M:%SZ)"

DRILL_LOGIN=false
DRILL_LIST=false
DRILL_REVOKE=false
DRILL_GATEWAY=false
READINESS_GAPS=()

if [[ -n "$BG_TOKEN_IN" ]]; then
  printf '%s' "$BG_TOKEN_IN" >"$TOKEN_FILE"
  DRILL_LOGIN=true
else
  set +e
  LOGIN_LINES="$(python3 "$HTTP_PY" login "$BASE_URL" "$EMERGENCY_TOKEN" "$LOGIN_REASON" 2>/dev/null)"
  LOGIN_EXIT=$?
  set -e
  if [[ "$LOGIN_EXIT" -ne 0 ]]; then
    READINESS_GAPS+=("break_glass_login_failed")
  else
    printf '%s' "$(echo "$LOGIN_LINES" | tail -n 1)" >"$TOKEN_FILE"
    DRILL_LOGIN=true
  fi
fi

if [[ ! -s "$TOKEN_FILE" ]]; then
  EVIDENCE="$(ENV="$ENVIRONMENT" GIT_SHA="$GIT_SHA" IMAGE_TAG="$IMAGE_TAG" python3 <<'PY'
import json, os
from datetime import datetime, timezone
print(json.dumps({
  "evidenceSchemaVersion": "break-glass-revocation-evidence-v1",
  "generatedAt": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
  "environment": os.environ["ENV"],
  "gitSha": os.environ["GIT_SHA"],
  "imageTag": os.environ["IMAGE_TAG"],
  "result": "readiness-gap",
  "skipReason": "",
  "readinessGaps": ["break_glass_login_failed"],
  "issuedAt": None, "expiresAt": None,
  "sessionIdShort": "", "jtiMasked": "", "revokeRefMasked": "",
  "revokedAt": None, "revokedByPresent": False, "reasonPresent": False,
  "gatewayRejectStatus": None, "gatewayRejectErrorCode": "",
  "auditEventsObserved": [], "metricsObserved": {},
  "drillSteps": {"loginAttempted": True, "sessionsListed": False, "revokeAttempted": False, "gatewayRejectVerified": False},
}))
PY
)"
  CODE="$(python3 "$REPORT_PY" --output-dir "$OUTPUT_DIR" --environment "$ENVIRONMENT" --evidence-json "$EVIDENCE" --github-summary)"
  exit "${CODE:-2}"
fi

BG_TOKEN="$(cat "$TOKEN_FILE")"
CLAIMS_JSON="$(python3 "$HTTP_PY" claims "$BG_TOKEN")"
REVOKE_REF="$(python3 -c "import json,sys; print(json.load(sys.stdin).get('break_glass_session_id',''))" <<<"$CLAIMS_JSON")"

LIST_JSON="$(python3 "$HTTP_PY" list "$BASE_URL" "$ADMIN_TOKEN")"
LIST_STATUS="$(python3 -c "import json,sys; print(json.load(sys.stdin).get('httpStatus',0))" <<<"$LIST_JSON")"
if [[ "$LIST_STATUS" -ge 200 && "$LIST_STATUS" -lt 300 ]]; then
  DRILL_LIST=true
else
  READINESS_GAPS+=("sessions_list_failed")
fi

REVOKE_REF_USE="$REVOKE_REF"
if [[ "$DRILL_LIST" == true ]]; then
  ALT="$(python3 <<'PY' "$LIST_JSON" "$REVOKE_REF"
import json, sys
body = json.loads(sys.argv[1]).get("body") or {}
target = sys.argv[2]
for item in body.get("items") or []:
    if item.get("tokenStatus") == "ACTIVE":
        if not target or item.get("revokeRef") == target:
            print(item.get("revokeRef") or "")
            break
PY
)"
  [[ -n "$ALT" ]] && REVOKE_REF_USE="$ALT"
fi

REVOKED_AT=""
REVOKED_BY_PRESENT=false
REASON_PRESENT=false
if [[ -n "$REVOKE_REF_USE" ]]; then
  REVOKE_JSON="$(python3 "$HTTP_PY" revoke "$BASE_URL" "$ADMIN_TOKEN" "$REVOKE_REF_USE" "$REVOKE_REASON")"
  REVOKE_STATUS="$(python3 -c "import json,sys; print(json.load(sys.stdin).get('httpStatus',0))" <<<"$REVOKE_JSON")"
  if [[ "$REVOKE_STATUS" -ge 200 && "$REVOKE_STATUS" -lt 300 ]]; then
    DRILL_REVOKE=true
    REVOKED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    REVOKED_BY_PRESENT=true
    REASON_PRESENT=true
  else
    READINESS_GAPS+=("revoke_failed")
  fi
else
  READINESS_GAPS+=("no_active_session_ref")
fi

echo "waiting ${CACHE_WAIT}s for denylist cache propagation..."
sleep "$CACHE_WAIT"

PROBE_JSON="$(python3 "$HTTP_PY" probe "$BASE_URL" "$TEST_PATH" "$BG_TOKEN")"
GATEWAY_STATUS="$(python3 -c "import json,sys; print(json.load(sys.stdin).get('httpStatus',0))" <<<"$PROBE_JSON")"
GATEWAY_CODE="$(python3 -c "import json,sys; print(json.load(sys.stdin).get('errorCode',''))" <<<"$PROBE_JSON")"

RESULT="failed"
if [[ "$GATEWAY_STATUS" == 401 && "$GATEWAY_CODE" == "BREAK_GLASS_TOKEN_REVOKED" ]]; then
  DRILL_GATEWAY=true
  RESULT="passed"
elif [[ ${#READINESS_GAPS[@]} -gt 0 ]]; then
  RESULT="readiness-gap"
elif [[ "$GATEWAY_STATUS" -ge 200 && "$GATEWAY_STATUS" -lt 300 ]]; then
  RESULT="failed"
fi

EVIDENCE="$(ENV="$ENVIRONMENT" GIT_SHA="$GIT_SHA" IMAGE_TAG="$IMAGE_TAG" \
  CLAIMS="$CLAIMS_JSON" RESULT="$RESULT" \
  REVOKED_AT="$REVOKED_AT" REVOKED_BY="$REVOKED_BY_PRESENT" REASON="$REASON_PRESENT" \
  GSTATUS="$GATEWAY_STATUS" GCODE="$GATEWAY_CODE" \
  REF_MASK="$(python3 -c "import json,sys; r=sys.argv[1]; print(r[:8]+'…' if len(r)>8 else r)" "$REVOKE_REF_USE")" \
  GAPS="$(if [[ ${#READINESS_GAPS[@]} -eq 0 ]]; then echo '[]'; else python3 -c "import json,sys; print(json.dumps(sys.argv[1:]))" "${READINESS_GAPS[@]}"; fi)" \
  DRILL_LOGIN="$DRILL_LOGIN" DRILL_LIST="$DRILL_LIST" DRILL_REVOKE="$DRILL_REVOKE" DRILL_GATEWAY="$DRILL_GATEWAY" \
  python3 <<'PY'
import json, os
from datetime import datetime, timezone

def mask_jti(j):
    j = (j or "").strip()
    return "****" if len(j) <= 8 else f"{j[:4]}…{j[-4:]}"

claims = json.loads(os.environ["CLAIMS"])
exp = claims.get("exp")
iat = claims.get("iat")
issued = datetime.fromtimestamp(iat, timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ") if iat else None
expires = datetime.fromtimestamp(exp, timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ") if exp else None
sid = claims.get("break_glass_session_id") or ""
evidence = {
  "evidenceSchemaVersion": "break-glass-revocation-evidence-v1",
  "generatedAt": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
  "environment": os.environ["ENV"],
  "gitSha": os.environ["GIT_SHA"],
  "imageTag": os.environ["IMAGE_TAG"],
  "result": os.environ["RESULT"],
  "skipReason": "",
  "issuedAt": issued,
  "expiresAt": expires,
  "sessionIdShort": sid[:12] if sid else "",
  "jtiMasked": mask_jti(claims.get("jti")),
  "revokeRefMasked": os.environ.get("REF_MASK", ""),
  "revokedAt": os.environ.get("REVOKED_AT") or None,
  "revokedByPresent": os.environ.get("REVOKED_BY") == "true",
  "reasonPresent": os.environ.get("REASON") == "true",
  "gatewayRejectStatus": int(os.environ.get("GSTATUS") or 0) or None,
  "gatewayRejectErrorCode": os.environ.get("GCODE") or "",
  "auditEventsObserved": [],
  "metricsObserved": {},
  "readinessGaps": json.loads(os.environ.get("GAPS") or "[]"),
  "drillSteps": {
    "loginAttempted": os.environ.get("DRILL_LOGIN") == "true",
    "sessionsListed": os.environ.get("DRILL_LIST") == "true",
    "revokeAttempted": os.environ.get("DRILL_REVOKE") == "true",
    "gatewayRejectVerified": os.environ.get("DRILL_GATEWAY") == "true",
  },
}
print(json.dumps(evidence))
PY
)"

EVIDENCE_FILE="$(mktemp)"
echo "$EVIDENCE" >"$EVIDENCE_FILE"
FORBIDDEN='Bearer [A-Za-z0-9._-]{8,}|eyJ[A-Za-z0-9_-]{10,}|access_token|Authorization:'
if grep -qE "$FORBIDDEN" "$EVIDENCE_FILE"; then
  python3 -c "import json; d=json.load(open('$EVIDENCE_FILE')); d['result']='privacy-failure'; json.dump(d, open('$EVIDENCE_FILE','w'), indent=2); print()"
  EVIDENCE="$(cat "$EVIDENCE_FILE")"
fi

FINAL_EXIT="$(python3 "$REPORT_PY" --output-dir "$OUTPUT_DIR" --environment "$ENVIRONMENT" \
  --evidence-json "$(cat "$EVIDENCE_FILE")" --github-summary)"
rm -f "$EVIDENCE_FILE"
bash "$VALIDATOR" "$OUTPUT_DIR/break-glass-revocation-evidence.json"
exit "${FINAL_EXIT:-0}"
