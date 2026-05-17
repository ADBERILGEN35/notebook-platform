#!/usr/bin/env bash
#
# Staging SCIM delta sandbox evidence orchestrator (Faz 121).
#
# Documented env (never log token values):
#   SCIM_DELTA_SANDBOX_GATEWAY_BASE_URL
#   SCIM_DELTA_SANDBOX_ADMIN_ACCESS_TOKEN
#   SCIM_DELTA_SANDBOX_PROVIDER          okta | entra | generic
#   SCIM_DELTA_SANDBOX_EXPECT_READY        true|false — readiness gap exit 2 if not certified
#
# Optional:
#   SCIM_DELTA_SANDBOX_OUTPUT_DIR          default ./scim-delta-sandbox-evidence-out
#
# Artifacts:
#   scim-delta-sandbox-evidence.json
#   scim-delta-certification-checklist.md
#   scim-delta-sandbox-summary.md
#
# Exit codes:
#   0 passed / needs-review / skipped
#   2 readiness gap (EXPECT_READY)
#   3 privacy violation
#   4 schema/shape validation failed
#   5 certification blocked

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

OUTPUT_DIR="${SCIM_DELTA_SANDBOX_OUTPUT_DIR:-$ROOT/scim-delta-sandbox-evidence-out}"
PROVIDER="${SCIM_DELTA_SANDBOX_PROVIDER:-generic}"
EXPECT_READY="${SCIM_DELTA_SANDBOX_EXPECT_READY:-false}"
GATEWAY_URL="${SCIM_DELTA_SANDBOX_GATEWAY_BASE_URL:-}"
ADMIN_TOKEN="${SCIM_DELTA_SANDBOX_ADMIN_ACCESS_TOKEN:-}"

mkdir -p "$OUTPUT_DIR"

expect_flag=""
if [[ "${EXPECT_READY,,}" == "true" || "${EXPECT_READY}" == "1" ]]; then
  expect_flag="--expect-ready"
fi

write_skip() {
  local reason="$1"
  python3 "$ROOT/scripts/scim/write-scim-delta-sandbox-report.py" \
    --output-dir "$OUTPUT_DIR" \
    --provider "$PROVIDER" \
    --skip "$reason" \
    $expect_flag \
    --github-summary
  exit 0
}

if [[ -z "$GATEWAY_URL" || -z "$ADMIN_TOKEN" ]]; then
  echo "Skipping SCIM delta sandbox evidence: gateway URL or admin token not configured."
  write_skip "missing_scim_delta_sandbox_secrets"
fi

export GATEWAY_BASE_URL="$GATEWAY_URL"
export ADMIN_ACCESS_TOKEN="$ADMIN_TOKEN"
export SCIM_DELTA_EVIDENCE_OUTPUT_DIR="$OUTPUT_DIR"
export SCIM_DELTA_VALIDATE_EVIDENCE=true

set +e
bash "$ROOT/scripts/scim/scim-delta-remote-fetch-smoke.sh"
SMOKE_EXIT=$?
set -e

cp -f "$OUTPUT_DIR/scim-delta-remote-fetch-evidence.json" "$OUTPUT_DIR/scim-delta-sandbox-evidence.json" 2>/dev/null || true

if [[ "$SMOKE_EXIT" -eq 2 ]]; then
  echo "privacy_violation from smoke script" >&2
  python3 "$ROOT/scripts/scim/write-scim-delta-sandbox-report.py" \
    --output-dir "$OUTPUT_DIR" --provider "$PROVIDER" $expect_flag --github-summary || true
  exit 3
fi

set +e
bash "$ROOT/scripts/scim/validate-scim-delta-evidence.sh" "$OUTPUT_DIR/scim-delta-sandbox-evidence.json"
VALIDATE_EXIT=$?
set -e

if [[ "$VALIDATE_EXIT" -eq 2 ]]; then
  echo "privacy_violation from validator" >&2
  exit 3
fi
if [[ "$VALIDATE_EXIT" -ne 0 ]]; then
  echo "evidence schema validation failed" >&2
  exit 4
fi

bash "$ROOT/scripts/scim/generate-scim-delta-certification-evidence-template.sh" "$PROVIDER" \
  >"$OUTPUT_DIR/scim-delta-certification-checklist.md"

FORBIDDEN='Bearer [A-Za-z0-9._-]{8,}|Authorization:|"userName"|skiptoken=|"nextCursor"[[:space:]]*:[[:space:]]*"[^"]{8,}"|@odata\.nextLink"[[:space:]]*:[[:space:]]*"https?://'
for f in "$OUTPUT_DIR/scim-delta-sandbox-evidence.json" \
         "$OUTPUT_DIR/scim-delta-certification-checklist.md" \
         "$OUTPUT_DIR/scim-delta-sandbox-summary.md"; do
  if [[ -f "$f" ]] && grep -qE "$FORBIDDEN" "$f"; then
    echo "privacy_violation: forbidden pattern in $f" >&2
    exit 3
  fi
done

FINAL_EXIT="$(python3 "$ROOT/scripts/scim/write-scim-delta-sandbox-report.py" \
  --output-dir "$OUTPUT_DIR" \
  --provider "$PROVIDER" \
  $expect_flag \
  --github-summary)"
exit "${FINAL_EXIT:-0}"
