#!/usr/bin/env bash
#
# Download PP-1..PP-3 GitHub Actions artifacts into pp-input layout (Faz 133 / Faz 135).
# No secret values logged. Writes download-manifest.json (run ids + status only).
#
# Usage:
#   bash scripts/security/download-backend-pp-artifacts.sh \
#     --output-base live-pp-evidence-out \
#     --provider okta \
#     --latest \
#     --wait
#
# Or explicit run IDs (skip auto latest):
#   ... --scim-run-id 12345 --break-glass-run-id 67890

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

OUTPUT_BASE="pp-downloads"
PROVIDER="okta"
LATEST=false
WAIT=false
WAIT_TIMEOUT=1800
SCIM_RUN=""
BG_RUN=""
RET_RUN=""
INCLUDE_RETENTION=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --output-base|--output) OUTPUT_BASE="$2"; shift 2 ;;
    --provider) PROVIDER="$2"; shift 2 ;;
    --latest) LATEST=true; shift ;;
    --wait) WAIT=true; shift ;;
    --wait-timeout) WAIT_TIMEOUT="$2"; shift 2 ;;
    --scim-run-id) SCIM_RUN="$2"; shift 2 ;;
    --break-glass-run-id) BG_RUN="$2"; shift 2 ;;
    --retention-run-id) RET_RUN="$2"; shift 2 ;;
    --include-retention) INCLUDE_RETENTION=true; shift ;;
    -h|--help)
      sed -n '1,22p' "$0"
      exit 0
      ;;
    *) echo "unknown arg: $1" >&2; exit 2 ;;
  esac
done

if ! command -v gh >/dev/null 2>&1; then
  echo "error: gh CLI required" >&2
  exit 2
fi
if ! gh auth status >/dev/null 2>&1; then
  echo "error: gh not authenticated (run: gh auth login)" >&2
  exit 2
fi

mkdir -p "$OUTPUT_BASE"

latest_run() {
  local workflow="$1"
  gh run list --workflow "$workflow" --limit 1 --json databaseId,status,conclusion,name \
    -q '.[0] | "\(.databaseId)\t\(.status)\t\(.conclusion // "unknown")"'
}

wait_run() {
  local run_id="$1"
  local label="$2"
  echo "==> Waiting for $label run $run_id (timeout ${WAIT_TIMEOUT}s)"
  set +e
  gh run watch "$run_id" --exit-status --interval 15 2>/dev/null
  local ec=$?
  set -e
  if [[ $ec -ne 0 ]]; then
    echo "warning: $label run $run_id did not complete successfully (exit $ec)" >&2
    return 1
  fi
  return 0
}

if [[ "$LATEST" == true && -z "$SCIM_RUN" ]]; then
  read -r SCIM_RUN scim_st scim_co <<<"$(latest_run scim-delta-readiness.yml)"
  echo "==> Latest SCIM workflow run: $SCIM_RUN (status=$scim_st conclusion=$scim_co)"
fi
if [[ "$LATEST" == true && -z "$BG_RUN" ]]; then
  read -r BG_RUN bg_st bg_co <<<"$(latest_run break-glass-revocation-readiness.yml)"
  echo "==> Latest break-glass workflow run: $BG_RUN (status=$bg_st conclusion=$bg_co)"
fi
if [[ "$INCLUDE_RETENTION" == true && "$LATEST" == true && -z "$RET_RUN" ]]; then
  read -r RET_RUN ret_st ret_co <<<"$(latest_run retention-readiness.yml)"
  echo "==> Latest retention workflow run: $RET_RUN (status=$ret_st conclusion=$ret_co)"
fi

if [[ "$WAIT" == true ]]; then
  [[ -n "$SCIM_RUN" ]] && wait_run "$SCIM_RUN" "SCIM" || true
  [[ -n "$BG_RUN" ]] && wait_run "$BG_RUN" "break-glass" || true
  [[ -n "$RET_RUN" ]] && wait_run "$RET_RUN" "retention" || true
fi

RAW="$OUTPUT_BASE/raw-downloads"
SCIM_ART="scim-delta-sandbox-evidence-${PROVIDER}"
SCIM_RAW="$RAW/scim"
BG_RAW="$RAW/breakglass"
RET_RAW="$RAW/retention"
PP_INPUT="$OUTPUT_BASE/pp-input"

mkdir -p "$SCIM_RAW" "$BG_RAW" "$RET_RAW" "$PP_INPUT/scim" "$PP_INPUT/breakglass" "$PP_INPUT/retention"

scim_dl=skipped
bg_dl=skipped
ret_dl=skipped

if [[ -n "$SCIM_RUN" ]]; then
  echo "==> Download SCIM artifact ($SCIM_ART) from run $SCIM_RUN"
  gh run download "$SCIM_RUN" -n "$SCIM_ART" -D "$SCIM_RAW"
  scim_dl=ok
else
  echo "skip: no SCIM run id"
fi

if [[ -n "$BG_RUN" ]]; then
  echo "==> Download break-glass artifact from run $BG_RUN"
  gh run download "$BG_RUN" -n break-glass-revocation-evidence -D "$BG_RAW"
  bg_dl=ok
else
  echo "skip: no break-glass run id"
fi

if [[ "$INCLUDE_RETENTION" == true && -n "$RET_RUN" ]]; then
  echo "==> Download retention artifact from run $RET_RUN"
  gh run download "$RET_RUN" -n retention-staging-smoke-evidence -D "$RET_RAW"
  ret_dl=ok
fi

echo "==> Normalize to pp-input layout"
bash scripts/security/prepare-backend-preprod-evidence-inputs.sh \
  --output "$PP_INPUT" \
  --layout pp-input \
  --scim-dir "$SCIM_RAW" \
  --break-glass-dir "$BG_RAW" \
  --retention-dir "$RET_RAW" || true

MANIFEST="$OUTPUT_BASE/download-manifest.json"
GENERATED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -u)"
cat >"$MANIFEST" <<EOF
{
  "generatedAt": "${GENERATED_AT}",
  "provider": "${PROVIDER}",
  "runs": {
    "scim": { "databaseId": "${SCIM_RUN:-}", "download": "${scim_dl}" },
    "breakGlass": { "databaseId": "${BG_RUN:-}", "download": "${bg_dl}" },
    "retention": { "databaseId": "${RET_RUN:-}", "download": "${ret_dl}" }
  },
  "ppInputPaths": {
    "scim": "pp-input/scim/scim-delta-sandbox-evidence.json",
    "breakGlass": "pp-input/breakglass/break-glass-revocation-evidence.json",
    "retention": "pp-input/retention/retention-staging-smoke-results.json"
  }
}
EOF
python3 -m json.tool "$MANIFEST" >/dev/null

echo ""
echo "Download manifest: $MANIFEST"
echo "PP input files under: $PP_INPUT"
