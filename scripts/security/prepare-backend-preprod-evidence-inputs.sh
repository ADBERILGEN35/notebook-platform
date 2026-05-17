#!/usr/bin/env bash
#
# Collect PP-1..PP-3 staging artifacts into normalized paths (Faz 130 / Faz 135).
#
# Layout pp-input (default):
#   <output>/scim/scim-delta-sandbox-evidence.json
#   <output>/breakglass/break-glass-revocation-evidence.json
#   <output>/retention/retention-staging-smoke-results.json
#
# Layout flat (legacy):
#   <output>/scim-delta-sandbox-evidence.json
#   ...
#
# Usage:
#   bash scripts/security/prepare-backend-preprod-evidence-inputs.sh \
#     --output live-pp-evidence-out/pp-input \
#     --scim-dir ./downloads/scim-delta-sandbox-evidence-okta \
#     --break-glass-dir ./downloads/break-glass-revocation-evidence

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUTPUT="preprod-inputs"
LAYOUT="pp-input"
SCIM_DIR=""
BG_DIR=""
RET_DIR=""
STRICT=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --output) OUTPUT="$2"; shift 2 ;;
    --layout) LAYOUT="$2"; shift 2 ;;
    --scim-dir) SCIM_DIR="$2"; shift 2 ;;
    --break-glass-dir) BG_DIR="$2"; shift 2 ;;
    --retention-dir) RET_DIR="$2"; shift 2 ;;
    --strict) STRICT=true; shift ;;
    *) echo "unknown arg: $1" >&2; exit 2 ;;
  esac
done

find_one() {
  local dir="$1"
  local name="$2"
  if [[ -z "$dir" || ! -d "$dir" ]]; then
    return 1
  fi
  find "$dir" -name "$name" -print -quit 2>/dev/null
}

copy_if_found() {
  local src="$1"
  local dest="$2"
  local label="$3"
  if [[ -n "$src" && -f "$src" ]]; then
    mkdir -p "$(dirname "$dest")"
    cp "$src" "$dest"
    echo "  ok: $label -> $dest"
    return 0
  fi
  echo "  missing: $label"
  return 1
}

if [[ "$LAYOUT" == "pp-input" ]]; then
  SCIM_DEST="$OUTPUT/scim/scim-delta-sandbox-evidence.json"
  BG_DEST="$OUTPUT/breakglass/break-glass-revocation-evidence.json"
  RET_DEST="$OUTPUT/retention/retention-staging-smoke-results.json"
else
  mkdir -p "$OUTPUT"
  SCIM_DEST="$OUTPUT/scim-delta-sandbox-evidence.json"
  BG_DEST="$OUTPUT/break-glass-revocation-evidence.json"
  RET_DEST="$OUTPUT/retention-staging-smoke-results.json"
fi

SCIM_SRC="$(find_one "$SCIM_DIR" 'scim-delta-sandbox-evidence.json' || true)"
BG_SRC="$(find_one "$BG_DIR" 'break-glass-revocation-evidence.json' || true)"
RET_SRC="$(find_one "$RET_DIR" 'retention-staging-smoke-results.json' || true)"

MISSING=0
echo "==> Normalizing pre-prod evidence inputs (layout=$LAYOUT) -> $OUTPUT"
copy_if_found "$SCIM_SRC" "$SCIM_DEST" "PP-1 SCIM" || MISSING=$((MISSING + 1))
copy_if_found "$BG_SRC" "$BG_DEST" "PP-2 break-glass" || MISSING=$((MISSING + 1))
copy_if_found "$RET_SRC" "$RET_DEST" "PP-3 retention" || MISSING=$((MISSING + 1))

echo ""
echo "==> Bundle builder environment (paths only)"
echo "export PREPROD_SCIM_EVIDENCE_PATH=\"$ROOT/$SCIM_DEST\""
echo "export PREPROD_BREAK_GLASS_EVIDENCE_PATH=\"$ROOT/$BG_DEST\""
echo "export PREPROD_RETENTION_EVIDENCE_PATH=\"$ROOT/$RET_DEST\""

if [[ "$STRICT" == true && "$MISSING" -gt 0 ]]; then
  echo ""
  echo "strict mode: $MISSING required artifact(s) missing" >&2
  exit 1
fi

echo ""
echo "prepare-backend-preprod-evidence-inputs: done (missing count=$MISSING)"
