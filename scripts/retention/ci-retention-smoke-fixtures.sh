#!/usr/bin/env bash
#
# CI entrypoint: retention smoke fixture matrix + observability validation (Faz 108).
# No repository secrets required.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

echo "==> bash -n retention smoke scripts"
for script in scripts/retention/*-dry-run-smoke.sh; do
  bash -n "$script"
  echo "  syntax ok: $script"
done
bash -n scripts/retention/run-retention-staging-smoke.sh
bash -n scripts/retention/validate-retention-staging-artifact.sh
bash -n scripts/retention/render-retention-staging-github-summary.sh
bash -n scripts/retention/generate-retention-e2e-evidence-template.sh
echo "  syntax ok: staging smoke orchestrator + artifact helpers"

echo "==> workspace/search fixture matrix"
bash scripts/retention/test-workspace-search-retention-smoke-fixtures.sh

echo "==> content/notification fixture matrix"
bash scripts/retention/test-content-notification-retention-smoke-fixtures.sh

echo "==> observability assets"
bash scripts/retention/validate-retention-observability.sh

echo "==> staging smoke skip + artifact (no secrets)"
STAGING_OUT="$(mktemp -d)"
RETENTION_STAGING_SMOKE_OUTPUT_DIR="$STAGING_OUT" \
  bash scripts/retention/run-retention-staging-smoke.sh
python3 -m json.tool "$STAGING_OUT/retention-staging-smoke-results.json" >/dev/null
RETENTION_STAGING_SMOKE_OUTPUT_DIR="$STAGING_OUT" \
  bash scripts/retention/validate-retention-staging-artifact.sh
grep -q "skipped: missing staging secrets" "$STAGING_OUT/retention-staging-smoke-summary.md"
grep -q '"status": "skipped"' "$STAGING_OUT/retention-staging-smoke-results.json"
rm -rf "$STAGING_OUT"

echo "==> evidence template generator (no secrets/PII)"
EVIDENCE_TMP="$(mktemp)"
bash scripts/retention/generate-retention-e2e-evidence-template.sh >"$EVIDENCE_TMP"
FORBIDDEN_TEMPLATE='jdbc:|password|Bearer |ADMIN_ACCESS_TOKEN|noteBody|workspaceName|userEmail|snippet'
if grep -qE "$FORBIDDEN_TEMPLATE" "$EVIDENCE_TMP"; then
  echo "Forbidden pattern in evidence template output" >&2
  rm -f "$EVIDENCE_TMP"
  exit 1
fi
grep -q 'CR-PLACEHOLDER' "$EVIDENCE_TMP"
rm -f "$EVIDENCE_TMP"

echo "==> check-no-secrets"
bash scripts/check-no-secrets.sh

echo "Retention CI fixtures passed."
