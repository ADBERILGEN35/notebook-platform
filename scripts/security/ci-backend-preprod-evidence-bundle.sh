#!/usr/bin/env bash
#
# CI: backend pre-prod evidence bundle builder (Faz 125).

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"
FIXTURE_INPUT="$ROOT/scripts/security/fixtures/preprod-input"

echo "==> bash -n build-backend-preprod-evidence-bundle.sh"
bash -n scripts/security/build-backend-preprod-evidence-bundle.sh
python3 -m py_compile scripts/security/build_backend_preprod_evidence_bundle.py

echo "==> missing evidence → NO_GO"
OUT="$(mktemp -d)"
set +e
PREPROD_BUNDLE_OUTPUT_DIR="$OUT" \
  bash scripts/security/build-backend-preprod-evidence-bundle.sh
CODE=$?
set -e
[[ "$CODE" -eq 5 ]] || { echo "expected exit 5 got $CODE" >&2; exit 1; }
grep -q '"finalRecommendation": "NO_GO"' "$OUT/backend-preprod-evidence-bundle.json"
grep -q '"status": "missing"' "$OUT/backend-preprod-evidence-bundle.json"
rm -rf "$OUT"

echo "==> all pass (PP-3 not required) → GO"
OUT="$(mktemp -d)"
PREPROD_BUNDLE_OUTPUT_DIR="$OUT" \
  PREPROD_SCIM_EVIDENCE_PATH="$FIXTURE_INPUT/scim-delta-sandbox-evidence.json" \
  PREPROD_BREAK_GLASS_EVIDENCE_PATH="$FIXTURE_INPUT/break-glass-revocation-evidence.json" \
  PREPROD_RETENTION_EVIDENCE_PATH="$FIXTURE_INPUT/retention-staging-smoke-results.json" \
  RELEASE_CANDIDATE_ID="rc-fixture" \
  bash scripts/security/build-backend-preprod-evidence-bundle.sh
grep -q '"finalRecommendation": "GO"' "$OUT/backend-preprod-evidence-bundle.json"
grep -q '"status": "not_required"' "$OUT/backend-preprod-evidence-bundle.json"
rm -rf "$OUT"

echo "==> PP-3 required but retention missing → NO_GO"
OUT="$(mktemp -d)"
set +e
PREPROD_BUNDLE_OUTPUT_DIR="$OUT" \
  PREPROD_SCIM_EVIDENCE_PATH="$FIXTURE_INPUT/scim-delta-sandbox-evidence.json" \
  PREPROD_BREAK_GLASS_EVIDENCE_PATH="$FIXTURE_INPUT/break-glass-revocation-evidence.json" \
  PREPROD_PP3_REQUIRED=true \
  bash scripts/security/build-backend-preprod-evidence-bundle.sh
CODE=$?
set -e
[[ "$CODE" -eq 5 ]] || { echo "expected exit 5 got $CODE" >&2; exit 1; }
rm -rf "$OUT"

echo "backend pre-prod evidence bundle CI passed."
