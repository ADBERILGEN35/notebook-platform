#!/usr/bin/env bash
#
# Validate sanitized retention staging smoke artifacts (Faz 109).

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUTPUT_DIR="${RETENTION_STAGING_SMOKE_OUTPUT_DIR:-$ROOT/retention-staging-smoke-out}"
JSON="$OUTPUT_DIR/retention-staging-smoke-results.json"
MD="$OUTPUT_DIR/retention-staging-smoke-summary.md"

if [[ ! -f "$JSON" ]]; then
  echo "No results JSON at $JSON (skip validation when smoke did not run)" >&2
  exit 0
fi

python3 -m json.tool "$JSON" >/dev/null
echo "JSON artifact valid: $JSON"

if [[ -f "$MD" ]]; then
  echo "Markdown summary present: $MD"
else
  echo "Missing markdown summary: $MD" >&2
  exit 1
fi

# Forbidden content scan (token, raw body, common PII field names).
FORBIDDEN='Bearer |ADMIN_ACCESS_TOKEN|ACCESS_TOKEN=|noteBody|commentBody|userEmail|noteTitle|recipientEmail|workspaceName|invitationEmail|"targets"[[:space:]]*:|contentBlocks|documentText'
if grep -qE "$FORBIDDEN" "$JSON" "$MD" 2>/dev/null; then
  echo "Forbidden pattern detected in staging smoke artifact" >&2
  exit 1
fi

echo "Artifact privacy guardrail scan OK."
