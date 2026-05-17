#!/usr/bin/env bash
#
# Backend Docker / Testcontainers full verification gate (Faz 128).
# Complements Faz 127 RC gate (fast/targeted) with full Gradle check + RLS integration.
#
# Env:
#   DOCKER_CI_OUTPUT_DIR              default backend-docker-ci-out
#   BACKEND_DOCKER_GRADLE_TASKS       default "check rlsIntegrationTest"
#   BACKEND_DOCKER_ALLOW_SKIP         true → ENVIRONMENT_SKIPPED when Docker missing (local dev)
#   BACKEND_DOCKER_CI_REQUIRED        true on GitHub Actions (fail if Docker missing)
#   GIT_SHA
#
# Outputs:
#   backend-docker-ci-results.json
#   backend-docker-ci-summary.md
#
# Exit: 0 PASS or ENVIRONMENT_SKIPPED (local only), 1 FAIL

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

OUTPUT_DIR="${DOCKER_CI_OUTPUT_DIR:-$ROOT/backend-docker-ci-out}"
mkdir -p "$OUTPUT_DIR"
RESULTS_TSV="$(mktemp)"
export DOCKER_CI_RESULTS_TSV="$RESULTS_TSV"
export DOCKER_CI_OUTPUT_DIR="$OUTPUT_DIR"
export GIT_SHA="${GIT_SHA:-unknown}"
export BACKEND_DOCKER_GRADLE_TASKS="${BACKEND_DOCKER_GRADLE_TASKS:-check rlsIntegrationTest}"

if [[ "${GITHUB_ACTIONS:-}" == "true" ]]; then
  export BACKEND_DOCKER_CI_REQUIRED="${BACKEND_DOCKER_CI_REQUIRED:-true}"
else
  export BACKEND_DOCKER_CI_REQUIRED="${BACKEND_DOCKER_CI_REQUIRED:-false}"
fi

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

run_skip_all() {
  local detail="$1"
  run_skip "docker-version" "ENVIRONMENT_SKIPPED" "$detail"
  run_skip "docker-info" "ENVIRONMENT_SKIPPED" "$detail"
  run_skip "testcontainers-sanity" "ENVIRONMENT_SKIPPED" "$detail"
  run_skip "gradle-full-check" "ENVIRONMENT_SKIPPED" "$detail"
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

docker_available() {
  command -v docker >/dev/null 2>&1 && docker version >/dev/null 2>&1
}

echo "Backend Docker CI gate"
echo "  output: $OUTPUT_DIR"
echo "  gitSha: ${GIT_SHA}"
echo "  gradleTasks: ${BACKEND_DOCKER_GRADLE_TASKS}"
echo "  required: ${BACKEND_DOCKER_CI_REQUIRED}"
echo "  allowSkip: ${BACKEND_DOCKER_ALLOW_SKIP:-false}"

bash -n scripts/security/ci-backend-docker-check.sh

if ! docker_available; then
  if [[ "${BACKEND_DOCKER_ALLOW_SKIP:-false}" == "true" ]] \
    && [[ "${BACKEND_DOCKER_CI_REQUIRED}" != "true" ]]; then
    echo ""
    echo "Docker not available — ENVIRONMENT_SKIPPED (local allow-skip mode)"
    run_skip_all "docker not installed or daemon not running"
    VERDICT="$(python3 "$ROOT/scripts/security/build_backend_docker_ci_report.py")"
    echo "Docker CI verdict: $VERDICT"
    exit 0
  fi
  record "docker-version" "fail" "DOCKER_UNAVAILABLE" "docker not available"
  echo "✗ Docker required but not available" >&2
  python3 "$ROOT/scripts/security/build_backend_docker_ci_report.py" || true
  exit 1
fi

run_check "docker-version" "DOCKER_UNAVAILABLE" docker version || FAILURES=$((FAILURES + 1))

run_check "docker-info" "DOCKER_UNAVAILABLE" docker info >/dev/null || FAILURES=$((FAILURES + 1))

# Minimal container pull/run sanity (Testcontainers prerequisite)
run_check "testcontainers-sanity" "TESTCONTAINERS_FAILURE" \
  docker run --rm alpine:3.20 echo testcontainers-sanity-ok || FAILURES=$((FAILURES + 1))

if [[ "$FAILURES" -gt 0 ]]; then
  echo "Docker checks failed; skipping Gradle" >&2
  python3 "$ROOT/scripts/security/build_backend_docker_ci_report.py" || true
  exit 1
fi

# Testcontainers env hints (non-secret)
export TESTCONTAINERS_RYUK_DISABLED="${TESTCONTAINERS_RYUK_DISABLED:-false}"
export TESTCONTAINERS_CHECKS_DISABLE="${TESTCONTAINERS_CHECKS_DISABLE:-false}"
echo "  TESTCONTAINERS_RYUK_DISABLED=${TESTCONTAINERS_RYUK_DISABLED}"

run_gradle() {
  # shellcheck disable=SC2086
  ./gradlew --no-daemon ${BACKEND_DOCKER_GRADLE_TASKS}
}

if run_check "gradle-full-check" "GRADLE_CHECK_FAILURE" run_gradle; then
  :
else
  FAILURES=$((FAILURES + 1))
fi

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "▶ Report"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

VERDICT="$(python3 "$ROOT/scripts/security/build_backend_docker_ci_report.py")"
echo ""
echo "Docker CI verdict: $VERDICT"
echo "  $OUTPUT_DIR/backend-docker-ci-results.json"
echo "  $OUTPUT_DIR/backend-docker-ci-summary.md"

if [[ "$VERDICT" == "FAIL" ]] || [[ "$FAILURES" -gt 0 ]]; then
  exit 1
fi
exit 0
