#!/usr/bin/env bash
#
# CI: check-staging-pp-secrets.sh (Faz 133)

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

FORBIDDEN='Bearer [A-Za-z0-9._-]{8,}|eyJ[A-Za-z0-9_-]{10,}|"access_token"|Authorization:|password|jdbc:'

echo "==> bash -n check-staging-pp-secrets.sh"
bash -n scripts/security/check-staging-pp-secrets.sh

echo "==> --print-required (names only)"
REQ_TMP="$(mktemp)"
bash scripts/security/check-staging-pp-secrets.sh --print-required >"$REQ_TMP"
grep -q 'SCIM_DELTA_SANDBOX_ADMIN_ACCESS_TOKEN' "$REQ_TMP"
grep -q 'BREAK_GLASS_STAGING_EMERGENCY_TOKEN' "$REQ_TMP"
if grep -qE "$FORBIDDEN" "$REQ_TMP"; then
  echo "privacy_violation: forbidden pattern in --print-required output" >&2
  exit 2
fi
rm -f "$REQ_TMP"

echo "==> missing secrets (expect exit 1, no token values)"
OUT="$(mktemp)"
set +e
bash scripts/security/check-staging-pp-secrets.sh >"$OUT" 2>&1
EC=$?
set -e
test "$EC" -eq 1
grep -q 'MISSING_SECRET' "$OUT"
grep -q 'PP-1 SCIM' "$OUT"
if grep -qE "$FORBIDDEN" "$OUT"; then
  echo "privacy_violation: forbidden pattern in probe output" >&2
  exit 2
fi
rm -f "$OUT"

echo "==> json mode"
JSON_TMP="$(mktemp)"
set +e
bash scripts/security/check-staging-pp-secrets.sh --json >"$JSON_TMP" 2>/dev/null
set -e
python3 -m json.tool "$JSON_TMP" >/dev/null
rm -f "$JSON_TMP"

echo "check-staging-pp-secrets CI passed."
