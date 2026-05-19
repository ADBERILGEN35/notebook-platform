#!/usr/bin/env bash
#
# CI: generate-frontend-rc-signoff-template.sh (Faz 147)

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

# Match credential-like values, not instructional prose (e.g. avoid bare "Bearer" word).
FORBIDDEN='Bearer [A-Za-z0-9._-]{8,}|eyJ[A-Za-z0-9_-]{10,}|"access_token"|Authorization: [A-Za-z]|password=[^ ]|jdbc:[a-z]|github_pat_[A-Za-z0-9]{20,}|ghp_[A-Za-z0-9]{20,}|rawPayload|nextCursor|@odata\.nextLink'

echo "==> bash -n generate-frontend-rc-signoff-template.sh"
bash -n scripts/security/generate-frontend-rc-signoff-template.sh

echo "==> generate template + forbidden pattern scan"
TMP="$(mktemp)"
bash scripts/security/generate-frontend-rc-signoff-template.sh \
  --rc-id fe-rc-ci-fixture \
  --output "$TMP"

if grep -qE "$FORBIDDEN" "$TMP"; then
  echo "privacy_violation: forbidden pattern in frontend sign-off template output" >&2
  grep -nE "$FORBIDDEN" "$TMP" >&2 || true
  rm -f "$TMP"
  exit 2
fi

grep -q 'Final decision' "$TMP"
grep -q 'NO_GO' "$TMP"
grep -q 'frontend-rc-readiness-results.json' "$TMP"
grep -q 'Visual QA checklist completed: \*\*no\*\*' "$TMP"
rm -f "$TMP"

echo "frontend RC sign-off template CI passed."
