#!/usr/bin/env bash
set -euo pipefail

FRONTEND_BASE_URL="${FRONTEND_BASE_URL:-http://localhost:3000}"

echo "Checking frontend health endpoint..."
curl --fail --silent --show-error "$FRONTEND_BASE_URL/healthz" >/dev/null

echo "Checking runtime config endpoint..."
runtime_config="$(curl --fail --silent --show-error "$FRONTEND_BASE_URL/runtime-config.js")"
case "$runtime_config" in
  *window.__NOTEBOOK_CONFIG__*) ;;
  *)
  echo "runtime-config.js does not expose window.__NOTEBOOK_CONFIG__" >&2
  exit 1
  ;;
esac

echo "Checking app shell and login render markers..."
index_html="$(curl --fail --silent --show-error "$FRONTEND_BASE_URL/")"
if ! printf '%s' "$index_html" | rg -q 'id="root"'; then
  echo "frontend index shell marker not found" >&2
  exit 1
fi

login_html="$(curl --fail --silent --show-error "$FRONTEND_BASE_URL/login")"
if ! printf '%s' "$login_html" | rg -q 'id="root"'; then
  echo "frontend login route did not serve SPA shell" >&2
  exit 1
fi

echo "Frontend smoke test completed."
