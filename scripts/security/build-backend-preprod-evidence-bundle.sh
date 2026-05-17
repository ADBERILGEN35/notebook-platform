#!/usr/bin/env bash
#
# Build sanitized backend pre-prod evidence bundle from PP-1..PP-3 inputs (Faz 125).
#
# Inputs (paths to JSON files; omit or leave unset if evidence missing):
#   PREPROD_SCIM_EVIDENCE_PATH          scim-delta-sandbox-evidence.json
#   PREPROD_BREAK_GLASS_EVIDENCE_PATH   break-glass-revocation-evidence.json
#   PREPROD_RETENTION_EVIDENCE_PATH      retention-staging-smoke-results.json
#
# Optional:
#   PREPROD_BUNDLE_OUTPUT_DIR
#   RELEASE_CANDIDATE_ID
#   PREPROD_PP3_REQUIRED=true|false     default false
#   PREPROD_ACCEPTED_RISKS_JSON         default []
#   PREPROD_HIGH_RISK_COUNT
#   GIT_SHA / IMAGE_TAG
#
# Outputs:
#   backend-preprod-evidence-bundle.json
#   backend-preprod-evidence-summary.md
#
# Exit: 0 GO / GO_WITH_ACCEPTED_RISKS, 3 privacy, 5 NO_GO

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

OUTPUT_DIR="${PREPROD_BUNDLE_OUTPUT_DIR:-$ROOT/backend-preprod-evidence-out}"
SCIM="${PREPROD_SCIM_EVIDENCE_PATH:-}"
BG="${PREPROD_BREAK_GLASS_EVIDENCE_PATH:-}"
RET="${PREPROD_RETENTION_EVIDENCE_PATH:-}"
PP3_REQUIRED="${PREPROD_PP3_REQUIRED:-false}"
ACCEPTED="${PREPROD_ACCEPTED_RISKS_JSON:-[]}"
HIGH_RISK="${PREPROD_HIGH_RISK_COUNT:-0}"
RC_ID="${RELEASE_CANDIDATE_ID:-}"

PP3_FLAG=""
if [[ "${PP3_REQUIRED,,}" == "true" || "${PP3_REQUIRED}" == "1" ]]; then
  PP3_FLAG="--pp3-required"
fi

FINAL_EXIT="$(python3 "$ROOT/scripts/security/build_backend_preprod_evidence_bundle.py" \
  --output-dir "$OUTPUT_DIR" \
  --release-candidate-id "$RC_ID" \
  --environment "${PREPROD_ENVIRONMENT:-staging}" \
  --scim-evidence "$SCIM" \
  --break-glass-evidence "$BG" \
  --retention-evidence "$RET" \
  --accepted-risks-json "$ACCEPTED" \
  --high-risk-count "$HIGH_RISK" \
  $PP3_FLAG \
  --github-summary)"

python3 -m json.tool "$OUTPUT_DIR/backend-preprod-evidence-bundle.json" >/dev/null

FORBIDDEN='Bearer [A-Za-z0-9._-]{8,}|eyJ[A-Za-z0-9_-]{10,}|"access_token"|Authorization:|password|jdbc:|rawPayload|nextCursor|@odata\.nextLink'
for f in "$OUTPUT_DIR/backend-preprod-evidence-bundle.json" "$OUTPUT_DIR/backend-preprod-evidence-summary.md"; do
  if grep -qE "$FORBIDDEN" "$f"; then
    echo "privacy_violation: forbidden pattern in $f" >&2
    exit 3
  fi
done

exit "${FINAL_EXIT:-0}"
