#!/usr/bin/env bash
#
# CI: generate-platform-rc-signoff-template.sh (Faz 148)

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

FORBIDDEN='Bearer [A-Za-z0-9._-]{8,}|eyJ[A-Za-z0-9_-]{10,}|"access_token"|Authorization: [A-Za-z]|password=[^ ]|jdbc:[a-z]|github_pat_[A-Za-z0-9]{20,}|ghp_[A-Za-z0-9]{20,}|rawPayload|nextCursor|@odata\.nextLink'

echo "==> bash -n generate-platform-rc-signoff-template.sh"
bash -n scripts/security/generate-platform-rc-signoff-template.sh

echo "==> generate template + forbidden pattern scan"
TMP="$(mktemp)"
bash scripts/security/generate-platform-rc-signoff-template.sh \
  --rc-id platform-rc-ci-fixture \
  --output "$TMP"

if grep -qE "$FORBIDDEN" "$TMP"; then
  echo "privacy_violation: forbidden pattern in platform sign-off template output" >&2
  grep -nE "$FORBIDDEN" "$TMP" >&2 || true
  rm -f "$TMP"
  exit 2
fi

grep -q 'Platform final decision' "$TMP"
grep -q 'NO_GO' "$TMP"
grep -q 'backend-preprod-evidence-bundle.json' "$TMP"
grep -q 'Visual QA checklist' "$TMP"
grep -q 'PP-3 Retention' "$TMP"
grep -q 'not_required' "$TMP"
rm -f "$TMP"

echo "platform RC sign-off template CI passed."
