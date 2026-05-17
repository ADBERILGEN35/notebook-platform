#!/usr/bin/env bash
#
# CI: generate-backend-rc-signoff-template.sh (Faz 131)

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

FORBIDDEN='Bearer [A-Za-z0-9._-]{8,}|eyJ[A-Za-z0-9_-]{10,}|"access_token"|Authorization:|password|jdbc:|rawPayload|nextCursor|@odata\.nextLink'

echo "==> bash -n generate-backend-rc-signoff-template.sh"
bash -n scripts/security/generate-backend-rc-signoff-template.sh

echo "==> generate template + forbidden pattern scan"
TMP="$(mktemp)"
bash scripts/security/generate-backend-rc-signoff-template.sh \
  --rc-id rc-ci-fixture \
  --output "$TMP"

if grep -qE "$FORBIDDEN" "$TMP"; then
  echo "privacy_violation: forbidden pattern in sign-off template output" >&2
  grep -nE "$FORBIDDEN" "$TMP" >&2 || true
  rm -f "$TMP"
  exit 2
fi

grep -q 'Final decision' "$TMP"
grep -q 'NO_GO' "$TMP"
grep -q 'backend-preprod-evidence-bundle' "$TMP"
rm -f "$TMP"

echo "backend RC sign-off template CI passed."
