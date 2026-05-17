#!/usr/bin/env bash
#
# Faz 135: Live staging PP evidence orchestrator (probe → dispatch → download → bundle).
# Never logs secret values. Sanitized reports only.
#
# Modes (combine or use --all):
#   --probe-only           Secret readiness probe; exit probe code
#   --dispatch-github      gh workflow run (skipped if MISSING_SECRET)
#   --download-artifacts   gh download + pp-input layout (--wait optional)
#   --build-bundle         Normalize (if needed) + bundle + run report
#   --all                  probe → dispatch → download → build (default operator path)
#
# Examples:
#   bash scripts/security/run-backend-live-staging-pp-evidence.sh \
#     --all --rc-id rc-2026-05-17 --provider okta --pp3-required false \
#     --output-base live-pp-evidence-out --check-github --wait-downloads
#
#   bash scripts/security/run-backend-live-staging-pp-evidence.sh \
#     --build-bundle --output-base live-pp-evidence-out \
#     --downloads-base pp-downloads --provider okta --rc-id rc-2026-05-17

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

RC_ID="rc-placeholder"
PROVIDER="generic"
PP3_REQUIRED=false
OUTPUT_BASE="$ROOT/live-pp-evidence-out"
GIT_SHA="${GIT_SHA:-unknown}"
IMAGE_TAG="${IMAGE_TAG:-staging}"
CHECK_GITHUB=false
SKIP_PROBE=false
WAIT_DOWNLOADS=false
WAIT_TIMEOUT=1800

DO_PROBE=false
DO_DISPATCH=false
DO_DOWNLOAD=false
DO_BUILD=false

# Legacy / local run
RUN_PP1=false
RUN_PP2=false
RUN_PP3=false
SCIM_DIR=""
BG_DIR=""
RET_DIR=""
DOWNLOADS_BASE=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --rc-id) RC_ID="$2"; shift 2 ;;
    --provider) PROVIDER="$2"; shift 2 ;;
    --pp3-required) PP3_REQUIRED="$2"; shift 2 ;;
    --output-base) OUTPUT_BASE="$2"; shift 2 ;;
    --probe-only) DO_PROBE=true; shift ;;
    --dispatch-github) DO_DISPATCH=true; shift ;;
    --download-artifacts) DO_DOWNLOAD=true; shift ;;
    --build-bundle) DO_BUILD=true; shift ;;
    --all) DO_PROBE=true; DO_DISPATCH=true; DO_DOWNLOAD=true; DO_BUILD=true; shift ;;
    --run-pp1) RUN_PP1=true; DO_BUILD=true; shift ;;
    --run-pp2) RUN_PP2=true; DO_BUILD=true; shift ;;
    --run-pp3) RUN_PP3=true; DO_BUILD=true; shift ;;
    --scim-dir) SCIM_DIR="$2"; DO_BUILD=true; shift 2 ;;
    --break-glass-dir) BG_DIR="$2"; DO_BUILD=true; shift 2 ;;
    --retention-dir) RET_DIR="$2"; DO_BUILD=true; shift 2 ;;
    --downloads-base) DOWNLOADS_BASE="$2"; DO_BUILD=true; shift 2 ;;
    --skip-probe) SKIP_PROBE=true; shift ;;
    --check-github) CHECK_GITHUB=true; shift ;;
    --wait-downloads) WAIT_DOWNLOADS=true; shift ;;
    --wait-timeout) WAIT_TIMEOUT="$2"; shift 2 ;;
    --git-sha) GIT_SHA="$2"; shift 2 ;;
    --image-tag) IMAGE_TAG="$2"; shift 2 ;;
    -h|--help)
      head -n 28 "$0" | tail -n +2
      exit 0
      ;;
    *) echo "unknown arg: $1" >&2; exit 2 ;;
  esac
done

# Default: build-bundle when artifact dirs supplied; else require explicit mode
if [[ "$DO_PROBE" == false && "$DO_DISPATCH" == false && "$DO_DOWNLOAD" == false && "$DO_BUILD" == false ]]; then
  DO_BUILD=true
fi

PP_INPUT="$OUTPUT_BASE/pp-input"
SCIM_FILE="$PP_INPUT/scim/scim-delta-sandbox-evidence.json"
BG_FILE="$PP_INPUT/breakglass/break-glass-revocation-evidence.json"
RET_FILE="$PP_INPUT/retention/retention-staging-smoke-results.json"
REPORT="$OUTPUT_BASE/live-pp-evidence-run-report.md"
MANIFEST="$OUTPUT_BASE/download-manifest.json"
BUNDLE_DIR="$OUTPUT_BASE"

SCIM_OUT="$OUTPUT_BASE/scim-delta-sandbox-evidence-out"
BG_OUT="$OUTPUT_BASE/break-glass-revocation-evidence-out"
RET_OUT="$OUTPUT_BASE/retention-staging-smoke-out"

mkdir -p "$PP_INPUT/scim" "$PP_INPUT/breakglass" "$PP_INPUT/retention" "$SCIM_OUT" "$BG_OUT" "$RET_OUT"

pp1_status="NOT_RUN"
pp2_status="NOT_RUN"
pp3_status="NOT_REQUIRED"
pp1_readiness="MISSING_SECRET"
pp2_readiness="MISSING_SECRET"
pp3_readiness="NOT_REQUIRED"
PROBE_EXIT=0
BUNDLE_EXIT=5
DISPATCH_RAN=false
DOWNLOAD_RAN=false

json_field() {
  local file="$1"
  local key="$2"
  python3 - "$file" "$key" <<'PY'
import json, sys
path, key = sys.argv[1], sys.argv[2]
try:
    with open(path) as f:
        d = json.load(f)
    cur = d
    for part in key.split("."):
        if isinstance(cur, dict):
            cur = cur.get(part)
        else:
            cur = None
            break
    print(cur if cur is not None else "")
except (OSError, json.JSONDecodeError):
    print("")
PY
}

readiness_ok() {
  local r="$1"
  [[ "$r" == "READY_LOCAL" || "$r" == "READY_GITHUB" ]]
}

run_probe() {
  local args=(--pp3-required "$PP3_REQUIRED")
  [[ "$CHECK_GITHUB" == true ]] && args+=(--check-github)
  echo "==> Staging PP secret probe"
  set +e
  bash scripts/security/check-staging-pp-secrets.sh "${args[@]}"
  PROBE_EXIT=$?
  set -e

  local probe_json
  probe_json="$(bash scripts/security/check-staging-pp-secrets.sh --json "${args[@]}" 2>/dev/null || true)"
  if [[ -n "$probe_json" ]]; then
    pp1_readiness="$(PROBE_JSON="$probe_json" python3 - <<'PY'
import json, os
d = json.loads(os.environ["PROBE_JSON"])
print(d.get("pp1", {}).get("readiness", "MISSING_SECRET"))
PY
)"
    pp2_readiness="$(PROBE_JSON="$probe_json" python3 - <<'PY'
import json, os
d = json.loads(os.environ["PROBE_JSON"])
print(d.get("pp2", {}).get("readiness", "MISSING_SECRET"))
PY
)"
    pp3_readiness="$(PROBE_JSON="$probe_json" python3 - <<'PY'
import json, os
d = json.loads(os.environ["PROBE_JSON"])
print(d.get("pp3", {}).get("readiness", "NOT_REQUIRED"))
PY
)"
  fi

  if ! readiness_ok "$pp1_readiness"; then
    pp1_status="MISSING_SECRET"
  fi
  if ! readiness_ok "$pp2_readiness"; then
    pp2_status="MISSING_SECRET"
  fi
  if [[ "${PP3_REQUIRED,,}" == "true" || "${PP3_REQUIRED}" == "1" ]]; then
    if [[ "$pp3_readiness" == "MISSING_SECRET" ]]; then
      pp3_status="MISSING_SECRET"
    fi
  fi

  echo "  PP-1 readiness: $pp1_readiness | PP-2: $pp2_readiness | PP-3: $pp3_readiness"
}

run_dispatch() {
  if ! readiness_ok "$pp1_readiness" || ! readiness_ok "$pp2_readiness"; then
    echo "==> Skipping workflow dispatch (MISSING_SECRET — configure secrets per docs/backend-staging-pp-secrets-governance.md)"
    return 0
  fi
  if ! command -v gh >/dev/null 2>&1; then
    echo "error: dispatch requires gh CLI" >&2
    return 2
  fi
  echo "==> Dispatching GitHub workflows"
  gh workflow run scim-delta-readiness.yml -f "provider=${PROVIDER}"
  gh workflow run break-glass-revocation-readiness.yml
  if [[ "${PP3_REQUIRED,,}" == "true" || "${PP3_REQUIRED}" == "1" ]]; then
    gh workflow run retention-readiness.yml -f run_staging_smoke=true
  fi
  DISPATCH_RAN=true
  sleep 3
  SCIM_RUN="$(gh run list --workflow scim-delta-readiness.yml --limit 1 --json databaseId -q '.[0].databaseId' 2>/dev/null || true)"
  BG_RUN="$(gh run list --workflow break-glass-revocation-readiness.yml --limit 1 --json databaseId -q '.[0].databaseId' 2>/dev/null || true)"
  echo "  Dispatched SCIM run id: ${SCIM_RUN:-unknown}"
  echo "  Dispatched break-glass run id: ${BG_RUN:-unknown}"
  echo "  Wait for completion: gh run watch <id> or re-run with --download-artifacts --wait-downloads"
}

run_download() {
  if [[ -n "$DOWNLOADS_BASE" ]]; then
    if [[ -f "$DOWNLOADS_BASE/pp-input/scim/scim-delta-sandbox-evidence.json" ]]; then
      echo "==> Using existing pp-input under --downloads-base"
      cp -f "$DOWNLOADS_BASE/pp-input/scim/"* "$PP_INPUT/scim/" 2>/dev/null || true
      cp -f "$DOWNLOADS_BASE/pp-input/breakglass/"* "$PP_INPUT/breakglass/" 2>/dev/null || true
      cp -f "$DOWNLOADS_BASE/pp-input/retention/"* "$PP_INPUT/retention/" 2>/dev/null || true
    else
      SCIM_DIR="$DOWNLOADS_BASE/scim-delta-sandbox-evidence-${PROVIDER}"
      BG_DIR="$DOWNLOADS_BASE/break-glass-revocation-evidence"
      RET_DIR="$DOWNLOADS_BASE/retention-staging-smoke-evidence"
    fi
    DOWNLOAD_RAN=true
    return 0
  fi

  if ! readiness_ok "$pp1_readiness" || ! readiness_ok "$pp2_readiness"; then
    echo "==> Skipping artifact download (MISSING_SECRET — configure secrets before live run)"
    return 0
  fi

  if ! command -v gh >/dev/null 2>&1; then
    echo "skip download: gh CLI not available"
    return 0
  fi

  local dl_args=(--output-base "$OUTPUT_BASE" --provider "$PROVIDER" --latest)
  [[ "$WAIT_DOWNLOADS" == true ]] && dl_args+=(--wait --wait-timeout "$WAIT_TIMEOUT")
  [[ "${PP3_REQUIRED,,}" == "true" || "${PP3_REQUIRED}" == "1" ]] && dl_args+=(--include-retention)

  echo "==> Downloading workflow artifacts"
  bash scripts/security/download-backend-pp-artifacts.sh "${dl_args[@]}"
  DOWNLOAD_RAN=true
}

validate_artifacts() {
  echo "==> Validating artifact JSON (sanitized)"
  if [[ -f "$SCIM_FILE" ]]; then
    python3 -m json.tool "$SCIM_FILE" >/dev/null
    bash scripts/scim/validate-scim-delta-evidence.sh "$SCIM_FILE"
  fi
  if [[ -f "$BG_FILE" ]]; then
    python3 -m json.tool "$BG_FILE" >/dev/null
    bash scripts/security/validate-break-glass-revocation-evidence.sh "$BG_FILE"
  fi
  if [[ -f "$RET_FILE" ]]; then
    python3 -m json.tool "$RET_FILE" >/dev/null
    bash scripts/retention/validate-retention-staging-artifact.sh 2>/dev/null || true
  fi
}

evaluate_pp_status() {
  if [[ -f "$SCIM_FILE" ]]; then
    local cert
    cert="$(json_field "$SCIM_FILE" "certificationResult")"
    if [[ "$cert" == "certified" ]]; then
      pp1_status="PASS"
    elif [[ -n "$cert" ]]; then
      pp1_status="FAIL"
    fi
  fi
  if [[ -f "$BG_FILE" ]]; then
    local result status code
    result="$(json_field "$BG_FILE" "result")"
    status="$(json_field "$BG_FILE" "gatewayRejectStatus")"
    code="$(json_field "$BG_FILE" "gatewayRejectErrorCode")"
    if [[ "$result" == "passed" && "$status" == "401" && "$code" == "BREAK_GLASS_TOKEN_REVOKED" ]]; then
      pp2_status="PASS"
    elif [[ -n "$result" ]]; then
      pp2_status="FAIL"
    fi
  fi
  if [[ "${PP3_REQUIRED,,}" == "true" || "${PP3_REQUIRED}" == "1" ]]; then
    if [[ -f "$RET_FILE" ]]; then
      local overall
      overall="$(json_field "$RET_FILE" "overall.status")"
      if [[ "$overall" == "passed" ]]; then pp3_status="PASS"
      elif [[ -n "$overall" ]]; then pp3_status="FAIL"
      fi
    elif [[ "$pp3_status" != "MISSING_SECRET" ]]; then
      pp3_status="NOT_RUN"
    fi
  fi
}

run_local_pp() {
  if [[ "$RUN_PP1" == true && "$pp1_status" != "MISSING_SECRET" ]]; then
    export SCIM_DELTA_SANDBOX_OUTPUT_DIR="$SCIM_OUT"
    export SCIM_DELTA_SANDBOX_PROVIDER="$PROVIDER"
    set +e
    bash scripts/scim/run-scim-delta-sandbox-evidence.sh
    set -e
    SCIM_DIR="$SCIM_OUT"
  fi
  if [[ "$RUN_PP2" == true && "$pp2_status" != "MISSING_SECRET" ]]; then
    export BREAK_GLASS_REVOCATION_DRILL_OUTPUT_DIR="$BG_OUT"
    set +e
    bash scripts/security/run-break-glass-revocation-evidence.sh
    set -e
    BG_DIR="$BG_OUT"
  fi
  if [[ "$RUN_PP3" == true && "$pp3_status" != "MISSING_SECRET" ]]; then
    export RETENTION_STAGING_SMOKE_OUTPUT_DIR="$RET_OUT"
    export API_BASE_URL="${RETENTION_STAGING_API_BASE_URL:-}"
    export ADMIN_ACCESS_TOKEN="${RETENTION_STAGING_ADMIN_ACCESS_TOKEN:-}"
    set +e
    bash scripts/retention/run-retention-staging-smoke.sh
    set -e
    RET_DIR="$RET_OUT"
  fi
}

normalize_inputs() {
  local prep=(--output "$PP_INPUT" --layout pp-input)
  [[ -n "$SCIM_DIR" ]] && prep+=(--scim-dir "$SCIM_DIR")
  [[ -n "$BG_DIR" ]] && prep+=(--break-glass-dir "$BG_DIR")
  [[ -n "$RET_DIR" ]] && prep+=(--retention-dir "$RET_DIR")
  echo "==> Normalizing to pp-input layout"
  bash scripts/security/prepare-backend-preprod-evidence-inputs.sh "${prep[@]}" || true
}

run_build_bundle() {
  run_local_pp
  normalize_inputs
  validate_artifacts
  evaluate_pp_status

  echo "==> Building pre-prod evidence bundle"
  export PREPROD_BUNDLE_OUTPUT_DIR="$BUNDLE_DIR"
  export RELEASE_CANDIDATE_ID="$RC_ID"
  export GIT_SHA="$GIT_SHA"
  export IMAGE_TAG="$IMAGE_TAG"
  export PREPROD_PP3_REQUIRED="$PP3_REQUIRED"
  export PREPROD_SCIM_EVIDENCE_PATH="$SCIM_FILE"
  export PREPROD_BREAK_GLASS_EVIDENCE_PATH="$BG_FILE"
  export PREPROD_RETENTION_EVIDENCE_PATH="$RET_FILE"

  set +e
  bash scripts/security/build-backend-preprod-evidence-bundle.sh
  BUNDLE_EXIT=$?
  set -e
}

write_run_report() {
  local final_rec
  final_rec="$(json_field "$BUNDLE_DIR/backend-preprod-evidence-bundle.json" "finalRecommendation")"
  [[ -z "$final_rec" ]] && final_rec="NO_GO"
  local generated_at
  generated_at="$(date -u +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -u)"

  cat >"$REPORT" <<EOF
# Live PP evidence run report — ${RC_ID}

Generated: ${generated_at}
Script: \`scripts/security/run-backend-live-staging-pp-evidence.sh\` (Faz 135)

> Sanitized report — no tokens, JWTs, or raw API payloads.

## Modes executed

| Step | Ran |
|------|-----|
| probe | ${DO_PROBE} |
| dispatch-github | ${DISPATCH_RAN} |
| download-artifacts | ${DOWNLOAD_RAN} |
| build-bundle | ${DO_BUILD} |

## Secret readiness (names only)

| PP | Readiness | Run status |
|----|-----------|------------|
| PP-1 | ${pp1_readiness} | **${pp1_status}** |
| PP-2 | ${pp2_readiness} | **${pp2_status}** |
| PP-3 | ${pp3_readiness} | **${pp3_status}** (pp3_required=${PP3_REQUIRED}) |

## PP input paths

| PP | Path |
|----|------|
| PP-1 | pp-input/scim/scim-delta-sandbox-evidence.json |
| PP-2 | pp-input/breakglass/break-glass-revocation-evidence.json |
| PP-3 | pp-input/retention/retention-staging-smoke-results.json |

## Bundle

| Field | Value |
|-------|--------|
| finalRecommendation | **${final_rec}** |
| Output directory | ${OUTPUT_BASE} |

## Outputs

- \`live-pp-evidence-run-report.md\` (this file)
- \`backend-preprod-evidence-bundle.json\`
- \`backend-preprod-evidence-summary.md\`
- \`download-manifest.json\` (when download step ran)

EOF
}

echo "==> Live staging PP evidence orchestrator (rc=$RC_ID provider=$PROVIDER)"

if [[ "$SKIP_PROBE" != true && ( "$DO_PROBE" == true || "$DO_DISPATCH" == true || "$DO_DOWNLOAD" == true || "$DO_BUILD" == true ) ]]; then
  run_probe
  if [[ "$DO_PROBE" == true && "$DO_DISPATCH" == false && "$DO_DOWNLOAD" == false && "$DO_BUILD" == false ]]; then
    exit "$PROBE_EXIT"
  fi
fi

if [[ "$DO_DISPATCH" == true ]]; then
  run_dispatch
fi

if [[ "$DO_DOWNLOAD" == true ]]; then
  run_download
fi

if [[ "$DO_BUILD" == true ]]; then
  run_build_bundle
  write_run_report
fi

if [[ "$DO_BUILD" == true ]]; then
  echo ""
  echo "PP-1: $pp1_status | PP-2: $pp2_status | PP-3: $pp3_status"
  echo "Bundle finalRecommendation: $(json_field "$BUNDLE_DIR/backend-preprod-evidence-bundle.json" "finalRecommendation")"
  echo "Report: $REPORT"
  echo "Bundle exit: $BUNDLE_EXIT"
  exit "$BUNDLE_EXIT"
fi

# probe + dispatch/download only
if [[ "$PROBE_EXIT" -ne 0 ]]; then
  exit "$PROBE_EXIT"
fi
exit 0
