#!/usr/bin/env bash
#
# CI: prepare-backend-preprod-evidence-inputs.sh (Faz 130)

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"
FIXTURE="$ROOT/scripts/security/fixtures/preprod-input"

echo "==> bash -n prepare-backend-preprod-evidence-inputs.sh"
bash -n scripts/security/prepare-backend-preprod-evidence-inputs.sh

echo "==> copy fixture inputs (all present)"
OUT="$(mktemp -d)"
bash scripts/security/prepare-backend-preprod-evidence-inputs.sh \
  --output "$OUT" \
  --layout pp-input \
  --scim-dir "$FIXTURE" \
  --break-glass-dir "$FIXTURE" \
  --retention-dir "$FIXTURE" \
  --strict
test -f "$OUT/scim/scim-delta-sandbox-evidence.json"
test -f "$OUT/breakglass/break-glass-revocation-evidence.json"
test -f "$OUT/retention/retention-staging-smoke-results.json"
rm -rf "$OUT"

echo "==> empty dirs (non-strict, missing expected)"
OUT="$(mktemp -d)"
OUT_LOG="$(bash scripts/security/prepare-backend-preprod-evidence-inputs.sh --output "$OUT" 2>&1)"
echo "$OUT_LOG" | grep -q 'missing: PP-1 SCIM'
rm -rf "$OUT"

echo "prepare pre-prod evidence inputs CI passed."
