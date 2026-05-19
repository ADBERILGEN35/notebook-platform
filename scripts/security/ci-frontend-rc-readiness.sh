#!/usr/bin/env bash
#
# Frontend release-candidate readiness gate (Faz 146).
# Aggregates secret scan, vitest, TypeScript, Playwright smoke, production build.
#
# Env:
#   RC_OUTPUT_DIR              default frontend-rc-readiness-out
#   RC_SKIP_PLAYWRIGHT         true to skip Playwright (PR lightweight)
#   RC_ALLOW_PLAYWRIGHT_SKIP   true (default) allows local skip when browsers missing
#   GIT_SHA / RELEASE_CANDIDATE_ID
#
# Outputs:
#   frontend-rc-readiness-results.json
#   frontend-rc-readiness-summary.md
#
# Exit: 0 PASS or PASS_WITH_ENVIRONMENT_SKIPS, 1 FAIL

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

OUTPUT_DIR="${RC_OUTPUT_DIR:-$ROOT/frontend-rc-readiness-out}"
mkdir -p "$OUTPUT_DIR"
RESULTS_TSV="$(mktemp)"
export RC_RESULTS_TSV="$RESULTS_TSV"
export RC_OUTPUT_DIR="$OUTPUT_DIR"
export GIT_SHA="${GIT_SHA:-unknown}"
export RC_ROOT="$ROOT"
export FRONTEND_DIR="$ROOT/frontend"

trap 'rm -f "$RESULTS_TSV"' EXIT

record() {
  local id="$1" status="$2" category="$3" detail="${4:-}"
  printf '%s|%s|%s|%s\n' "$id" "$status" "$category" "$detail" >>"$RESULTS_TSV"
}

run_check() {
  local id="$1"
  local category="$2"
  shift 2
  echo ""
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  echo "▶ $id"
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  if "$@"; then
    record "$id" "pass" "$category" ""
    echo "✓ $id PASS"
    return 0
  fi
  record "$id" "fail" "$category" "command failed"
  echo "✗ $id FAIL" >&2
  return 1
}

run_skip() {
  local id="$1" category="$2" detail="$3"
  echo ""
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  echo "▶ $id (skipped)"
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  record "$id" "skipped" "$category" "$detail"
  echo "○ $id SKIPPED: $detail"
}

FAILURES=0

echo "Frontend RC readiness gate"
echo "  output: $OUTPUT_DIR"
echo "  gitSha: ${GIT_SHA}"

bash -n scripts/security/ci-frontend-rc-readiness.sh

if ! command -v node >/dev/null 2>&1; then
  echo "ERROR: node is required (Node.js 20+). Install Node or add it to PATH." >&2
  exit 1
fi
NODE_MAJOR="$(node -p "Number(process.versions.node.split('.')[0])" 2>/dev/null || echo 0)"
if [[ "${NODE_MAJOR:-0}" -lt 20 ]]; then
  echo "ERROR: Node.js 20+ required; found: $(node -v 2>&1 || echo unknown)" >&2
  exit 1
fi

needs_playwright_skip() {
  if [[ "${RC_SKIP_PLAYWRIGHT:-false}" == "true" ]]; then
    return 0
  fi
  if [[ "${GITHUB_ACTIONS:-}" == "true" ]]; then
    return 1
  fi
  if [[ "${RC_ALLOW_PLAYWRIGHT_SKIP:-true}" != "true" ]]; then
    return 1
  fi
  if [[ ! -d "$FRONTEND_DIR/node_modules" ]]; then
    return 0
  fi
  if ! (cd "$FRONTEND_DIR" && npx playwright --version >/dev/null 2>&1); then
    return 0
  fi
  local dry
  dry="$(cd "$FRONTEND_DIR" && npx playwright install chromium --dry-run 2>&1 || true)"
  if echo "$dry" | grep -qiE 'Download|download'; then
    return 0
  fi
  return 1
}

run_check "check-no-secrets" "SECRET_FAILURE" bash scripts/check-no-secrets.sh || FAILURES=$((FAILURES + 1))

run_check "vitest-unit" "UNIT_TEST_FAILURE" bash -c "cd '$FRONTEND_DIR' && npm test" || FAILURES=$((FAILURES + 1))

run_check "typescript-build" "TYPESCRIPT_FAILURE" bash -c "cd '$FRONTEND_DIR' && npx tsc -b" || FAILURES=$((FAILURES + 1))

if needs_playwright_skip; then
  if [[ "${RC_SKIP_PLAYWRIGHT:-false}" == "true" ]]; then
    run_skip "playwright-smoke" "ENVIRONMENT_SKIPPED" "RC_SKIP_PLAYWRIGHT=true"
  else
    run_skip "playwright-smoke" "ENVIRONMENT_SKIPPED" "Playwright browsers not installed (local dev mode)"
  fi
else
  run_check "playwright-smoke" "PLAYWRIGHT_FAILURE" bash -c \
    "cd '$FRONTEND_DIR' && npx playwright test tests/e2e --project=chromium --workers=1" || FAILURES=$((FAILURES + 1))
fi

run_check "frontend-production-build" "BUILD_FAILURE" bash -c "cd '$FRONTEND_DIR' && npm run build" || FAILURES=$((FAILURES + 1))

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "▶ Report"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

VERDICT="$(python3 "$ROOT/scripts/security/build_frontend_rc_readiness_report.py")"
echo ""
echo "RC readiness verdict: $VERDICT"
echo "  $OUTPUT_DIR/frontend-rc-readiness-results.json"
echo "  $OUTPUT_DIR/frontend-rc-readiness-summary.md"

if [[ "$VERDICT" == "FAIL" ]] || [[ "$FAILURES" -gt 0 ]]; then
  exit 1
fi
exit 0
