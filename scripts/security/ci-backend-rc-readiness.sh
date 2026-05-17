#!/usr/bin/env bash
#
# Backend release-candidate readiness gate (Faz 127).
# Aggregates secret scan, dangerous defaults, fixture gates, flag plan, targeted Gradle tests, Helm.
#
# Env:
#   RC_OUTPUT_DIR          default backend-rc-readiness-out
#   RC_SKIP_GRADLE         true to skip targeted Gradle tests (PR lightweight / local)
#   RC_SKIP_HELM           true to skip Helm (else auto-skip if helm missing)
#   GIT_SHA / RELEASE_CANDIDATE_ID
#
# Outputs:
#   backend-rc-readiness-results.json
#   backend-rc-readiness-summary.md
#
# Exit: 0 PASS or PASS_WITH_ENVIRONMENT_SKIPS, 1 FAIL

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

OUTPUT_DIR="${RC_OUTPUT_DIR:-$ROOT/backend-rc-readiness-out}"
mkdir -p "$OUTPUT_DIR"
RESULTS_TSV="$(mktemp)"
export RC_RESULTS_TSV="$RESULTS_TSV"
export RC_OUTPUT_DIR="$OUTPUT_DIR"
export GIT_SHA="${GIT_SHA:-unknown}"

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

echo "Backend RC readiness gate"
echo "  output: $OUTPUT_DIR"
echo "  gitSha: ${GIT_SHA}"

bash -n scripts/security/ci-backend-rc-readiness.sh

# 1. Secret scan
run_check "check-no-secrets" "SECRET_FAILURE" bash scripts/check-no-secrets.sh || FAILURES=$((FAILURES + 1))

# 2. Dangerous defaults
run_check "dangerous-defaults" "DANGEROUS_DEFAULT_FAILURE" \
  bash scripts/security/ci-backend-production-readiness-review.sh || FAILURES=$((FAILURES + 1))

# 3. Production flag plan
run_check "production-flag-plan" "DANGEROUS_DEFAULT_FAILURE" \
  bash scripts/security/ci-production-flag-plan.sh || FAILURES=$((FAILURES + 1))

# 4. Pre-prod evidence bundle fixtures
run_check "preprod-evidence-bundle" "FIXTURE_FAILURE" \
  bash scripts/security/ci-backend-preprod-evidence-bundle.sh || FAILURES=$((FAILURES + 1))

# 5–7. Fixture gates
run_check "retention-smoke-fixtures" "FIXTURE_FAILURE" \
  bash scripts/retention/ci-retention-smoke-fixtures.sh || FAILURES=$((FAILURES + 1))

run_check "scim-delta-fixtures" "FIXTURE_FAILURE" \
  bash scripts/scim/ci-scim-delta-remote-fetch-fixtures.sh || FAILURES=$((FAILURES + 1))

run_check "break-glass-revocation-fixtures" "FIXTURE_FAILURE" \
  bash scripts/security/ci-break-glass-revocation-fixtures.sh || FAILURES=$((FAILURES + 1))

# 8. Helm template
if [[ "${RC_SKIP_HELM:-false}" == "true" ]]; then
  run_skip "helm-template-check" "ENVIRONMENT_SKIPPED" "RC_SKIP_HELM=true"
elif ! command -v helm >/dev/null 2>&1; then
  run_skip "helm-template-check" "ENVIRONMENT_SKIPPED" "helm not installed"
else
  run_check "helm-template-check" "HELM_RENDER_FAILURE" bash scripts/helm-template-check.sh || FAILURES=$((FAILURES + 1))
fi

# 9. Targeted Gradle tests
if [[ "${RC_SKIP_GRADLE:-false}" == "true" ]]; then
  run_skip "gradle-identity-breakglass-scim-admin" "ENVIRONMENT_SKIPPED" "RC_SKIP_GRADLE=true"
  run_skip "gradle-api-gateway-breakglass-scim-retention" "ENVIRONMENT_SKIPPED" "RC_SKIP_GRADLE=true"
  run_skip "gradle-content-retention" "ENVIRONMENT_SKIPPED" "RC_SKIP_GRADLE=true"
  run_skip "gradle-notification-platformretention" "ENVIRONMENT_SKIPPED" "RC_SKIP_GRADLE=true"
  run_skip "gradle-workspace-retention" "ENVIRONMENT_SKIPPED" "RC_SKIP_GRADLE=true"
  run_skip "gradle-search-retention" "ENVIRONMENT_SKIPPED" "RC_SKIP_GRADLE=true"
else
  if ! command -v java >/dev/null 2>&1 && [[ ! -x ./gradlew ]]; then
    run_skip "gradle-identity-breakglass-scim-admin" "ENVIRONMENT_SKIPPED" "java/gradlew unavailable"
    run_skip "gradle-api-gateway-breakglass-scim-retention" "ENVIRONMENT_SKIPPED" "java/gradlew unavailable"
    run_skip "gradle-content-retention" "ENVIRONMENT_SKIPPED" "java/gradlew unavailable"
    run_skip "gradle-notification-platformretention" "ENVIRONMENT_SKIPPED" "java/gradlew unavailable"
    run_skip "gradle-workspace-retention" "ENVIRONMENT_SKIPPED" "java/gradlew unavailable"
    run_skip "gradle-search-retention" "ENVIRONMENT_SKIPPED" "java/gradlew unavailable"
  else
    run_check "gradle-identity-breakglass-scim-admin" "BACKEND_TEST_FAILURE" \
      ./gradlew --no-daemon :identity-service:test \
        --tests "*BreakGlass*" --tests "*Scim*" --tests "*Admin*" || FAILURES=$((FAILURES + 1))

    run_check "gradle-api-gateway-breakglass-scim-retention" "BACKEND_TEST_FAILURE" \
      ./gradlew --no-daemon :api-gateway:test \
        --tests "*BreakGlass*" --tests "*Scim*" --tests "*AdminPlatformRetention*" || FAILURES=$((FAILURES + 1))

    run_check "gradle-content-retention" "BACKEND_TEST_FAILURE" \
      ./gradlew --no-daemon :content-service:test --tests "*Retention*" || FAILURES=$((FAILURES + 1))

    run_check "gradle-notification-platformretention" "BACKEND_TEST_FAILURE" \
      ./gradlew --no-daemon :notification-service:test --tests "*platformretention*" || FAILURES=$((FAILURES + 1))

    run_check "gradle-workspace-retention" "BACKEND_TEST_FAILURE" \
      ./gradlew --no-daemon :workspace-service:test --tests "*WorkspaceRetention*" || FAILURES=$((FAILURES + 1))

    run_check "gradle-search-retention" "BACKEND_TEST_FAILURE" \
      ./gradlew --no-daemon :search-service:test --tests "*SearchRetention*" || FAILURES=$((FAILURES + 1))
  fi
fi

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "▶ Report"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

VERDICT="$(python3 "$ROOT/scripts/security/build_backend_rc_readiness_report.py")"
echo ""
echo "RC readiness verdict: $VERDICT"
echo "  $OUTPUT_DIR/backend-rc-readiness-results.json"
echo "  $OUTPUT_DIR/backend-rc-readiness-summary.md"

if [[ "$VERDICT" == "FAIL" ]] || [[ "$FAILURES" -gt 0 ]]; then
  exit 1
fi
exit 0
