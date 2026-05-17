#!/usr/bin/env bash
#
# Render GitHub Actions step summary from retention staging smoke results (Faz 109).

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUTPUT_DIR="${RETENTION_STAGING_SMOKE_OUTPUT_DIR:-$ROOT/retention-staging-smoke-out}"

if [[ -z "${GITHUB_STEP_SUMMARY:-}" ]]; then
  echo "GITHUB_STEP_SUMMARY not set; skipping summary render."
  exit 0
fi

python3 "$ROOT/scripts/retention/write-retention-staging-report.py" \
  --output-dir "$OUTPUT_DIR" \
  --render-summary-only

echo "GitHub step summary updated."
