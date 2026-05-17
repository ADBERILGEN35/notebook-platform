#!/usr/bin/env bash
#
# CI: break-glass revocation drill fixtures (Faz 123). No live staging secrets.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

echo "==> bash -n scripts/security/break-glass-revocation*.sh"
for script in scripts/security/break-glass-revocation*.sh scripts/security/run-break-glass-revocation-evidence.sh; do
  [[ -f "$script" ]] || continue
  bash -n "$script"
  echo "  syntax ok: $script"
done
python3 -m py_compile scripts/security/write-break-glass-revocation-report.py
python3 -m py_compile scripts/security/break-glass-revocation-drill-http.py
echo "  syntax ok: python helpers"

echo "==> orchestrator skip path"
OUT="$(mktemp -d)"
BREAK_GLASS_REVOCATION_DRILL_OUTPUT_DIR="$OUT" bash scripts/security/run-break-glass-revocation-evidence.sh
python3 -m json.tool "$OUT/break-glass-revocation-evidence.json" >/dev/null
grep -q '"result": "skipped"' "$OUT/break-glass-revocation-evidence.json"
test -f "$OUT/break-glass-revocation-summary.md"
rm -rf "$OUT"

echo "==> validate fixtures"
for name in passed skipped failed readiness-gap; do
  bash scripts/security/validate-break-glass-revocation-evidence.sh \
    "scripts/security/fixtures/${name}-evidence.json"
done

echo "==> privacy fixture must fail validator"
set +e
bash scripts/security/validate-break-glass-revocation-evidence.sh \
  scripts/security/fixtures/privacy-failure-evidence.json
PRIV=$?
set -e
[[ "$PRIV" -eq 2 ]] || { echo "expected privacy exit 2, got $PRIV" >&2; exit 1; }

echo "==> fixture modes"
for pair in passed:0 failed:5 readiness-gap:2; do
  name="${pair%%:*}"
  want="${pair##*:}"
  OUT="$(mktemp -d)"
  set +e
  BREAK_GLASS_REVOCATION_DRILL_FIXTURE="$name" \
    BREAK_GLASS_REVOCATION_DRILL_OUTPUT_DIR="$OUT" \
    bash scripts/security/break-glass-revocation-drill.sh
  got=$?
  set -e
  rm -rf "$OUT"
  [[ "$got" -eq "$want" ]] || { echo "fixture $name expected exit $want got $got" >&2; exit 1; }
done

echo "break-glass revocation CI fixtures passed."
