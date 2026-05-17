#!/usr/bin/env bash
#
# CI: production flag plan validator (Faz 126).

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"
UNSAFE="$ROOT/scripts/security/fixtures/production-flag-plan-unsafe/values-unsafe.yaml"

echo "==> bash -n validate-production-flag-plan.sh"
bash -n scripts/security/validate-production-flag-plan.sh

echo "==> chart + prod overlay (expect PASS)"
bash scripts/security/validate-production-flag-plan.sh

echo "==> unsafe fixture (expect FAIL)"
set +e
bash scripts/security/validate-production-flag-plan.sh --values-file "$UNSAFE"
CODE=$?
set -e
[[ "$CODE" -ne 0 ]] || { echo "expected non-zero on unsafe fixture" >&2; exit 1; }

echo "production flag plan CI passed."
