#!/usr/bin/env bash
#
# CI: run-backend-live-staging-pp-evidence.sh (Faz 132) — fixture path + missing-secret path

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"
FIXTURE="$ROOT/scripts/security/fixtures/preprod-input"

echo "==> bash -n orchestrator + check-staging-pp-secrets"
bash -n scripts/security/run-backend-live-staging-pp-evidence.sh
bash -n scripts/security/check-staging-pp-secrets.sh

echo "==> probe-only (missing secrets)"
set +e
bash scripts/security/run-backend-live-staging-pp-evidence.sh --probe-only --pp3-required false
PROBE_EC=$?
set -e
test "$PROBE_EC" -eq 1

echo "==> build-bundle only → NO_GO"
OUT="$(mktemp -d)"
set +e
bash scripts/security/run-backend-live-staging-pp-evidence.sh \
  --build-bundle \
  --rc-id rc-ci-missing \
  --output-base "$OUT" \
  --pp3-required false \
  --skip-probe
MISS_EXIT=$?
set -e
grep -q 'NO_GO' "$OUT/live-pp-evidence-run-report.md"
grep -q 'MISSING_SECRET\|NOT_RUN' "$OUT/live-pp-evidence-run-report.md"
test -f "$OUT/backend-preprod-evidence-bundle.json"
test "$MISS_EXIT" -eq 5 || test "$MISS_EXIT" -eq 0
rm -rf "$OUT"

echo "==> fixture pp-input → bundle GO"
OUT="$(mktemp -d)"
mkdir -p "$OUT/pp-input/scim" "$OUT/pp-input/breakglass" "$OUT/pp-input/retention"
cp "$FIXTURE/scim-delta-sandbox-evidence.json" "$OUT/pp-input/scim/"
cp "$FIXTURE/break-glass-revocation-evidence.json" "$OUT/pp-input/breakglass/"
cp "$FIXTURE/retention-staging-smoke-results.json" "$OUT/pp-input/retention/"
bash scripts/security/run-backend-live-staging-pp-evidence.sh \
  --build-bundle \
  --rc-id rc-ci-fixture \
  --output-base "$OUT" \
  --pp3-required false \
  --skip-probe
grep -q 'GO' "$OUT/live-pp-evidence-run-report.md"
grep -q 'finalRecommendation' "$OUT/backend-preprod-evidence-bundle.json"
FORBIDDEN='Bearer [A-Za-z0-9._-]{8,}|eyJ[A-Za-z0-9_-]{10,}|"access_token"|Authorization:|password|jdbc:'
if grep -qE "$FORBIDDEN" "$OUT/live-pp-evidence-run-report.md"; then
  echo "privacy_violation: forbidden pattern in run report" >&2
  exit 2
fi
rm -rf "$OUT"

echo "live staging PP evidence orchestrator CI passed."
