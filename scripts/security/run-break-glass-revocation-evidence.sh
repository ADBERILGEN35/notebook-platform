#!/usr/bin/env bash
#
# Orchestrator for break-glass revocation staging evidence (Faz 123).

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

OUTPUT_DIR="${BREAK_GLASS_REVOCATION_DRILL_OUTPUT_DIR:-$ROOT/break-glass-revocation-evidence-out}"
export BREAK_GLASS_REVOCATION_DRILL_OUTPUT_DIR="$OUTPUT_DIR"

mkdir -p "$OUTPUT_DIR"

set +e
bash "$ROOT/scripts/security/break-glass-revocation-drill.sh"
DRILL_EXIT=$?
set -e

EVIDENCE="$OUTPUT_DIR/break-glass-revocation-evidence.json"
if [[ -f "$EVIDENCE" ]]; then
  python3 -m json.tool "$EVIDENCE" >/dev/null
  bash "$ROOT/scripts/security/validate-break-glass-revocation-evidence.sh" "$EVIDENCE"
fi

exit "${DRILL_EXIT:-0}"
