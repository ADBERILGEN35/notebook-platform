#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BASE_URL="${BASE_URL:-}"
RUN_SMOKE_TEST="${RUN_SMOKE_TEST:-false}"
RUN_AUTH_SECURITY_SMOKE="${RUN_AUTH_SECURITY_SMOKE:-false}"
RUN_RLS_STRICT_SMOKE="${RUN_RLS_STRICT_SMOKE:-false}"

if [[ -z "$BASE_URL" ]]; then
  echo "BASE_URL is required, for example BASE_URL=https://api.staging.example.com" >&2
  exit 1
fi

health_url="${BASE_URL%/}/actuator/health"
echo "Checking $health_url"
curl --fail --silent --show-error "$health_url" >/dev/null

if [[ "$RUN_SMOKE_TEST" == "true" ]]; then
  BASE_URL="$BASE_URL" bash "$ROOT_DIR/scripts/smoke-test.sh"
fi

if [[ "$RUN_AUTH_SECURITY_SMOKE" == "true" ]]; then
  BASE_URL="$BASE_URL" bash "$ROOT_DIR/scripts/smoke-test-auth-security.sh"
fi

if [[ "$RUN_RLS_STRICT_SMOKE" == "true" ]]; then
  BASE_URL="$BASE_URL" bash "$ROOT_DIR/scripts/smoke-test-rls-strict.sh"
fi

echo "GitOps post-sync checks completed."
