#!/usr/bin/env bash
set -euo pipefail

FRONTEND_BASE_URL="${FRONTEND_BASE_URL:-http://localhost:3000}"
EXPECT_CSP_MODE="${EXPECT_CSP_MODE:-either}" # enforce|report-only|disabled|either

echo "Checking frontend health endpoint..."
curl --fail --silent --show-error "$FRONTEND_BASE_URL/healthz" >/dev/null

echo "Checking response security headers..."
headers="$(curl --fail --silent --show-error -D - -o /dev/null "$FRONTEND_BASE_URL/")"
for header in "x-content-type-options: nosniff" \
  "x-frame-options: deny" \
  "referrer-policy: strict-origin-when-cross-origin" \
  "permissions-policy:" \
  "cross-origin-opener-policy: same-origin" \
  "cross-origin-resource-policy: same-origin"; do
  if ! printf '%s' "$headers" | rg -qi "^$header"; then
    echo "missing required header: $header" >&2
    exit 1
  fi
done

csp_enforce_present=false
csp_report_present=false
if printf '%s' "$headers" | rg -qi "^content-security-policy:"; then
  csp_enforce_present=true
fi
if printf '%s' "$headers" | rg -qi "^content-security-policy-report-only:"; then
  csp_report_present=true
fi

case "$EXPECT_CSP_MODE" in
enforce)
  [ "$csp_enforce_present" = true ] || { echo "expected CSP enforce header" >&2; exit 1; }
  ;;
report-only)
  [ "$csp_report_present" = true ] || { echo "expected CSP report-only header" >&2; exit 1; }
  ;;
disabled)
  if [ "$csp_enforce_present" = true ] || [ "$csp_report_present" = true ]; then
    echo "expected CSP to be disabled" >&2
    exit 1
  fi
  ;;
either)
  :
  ;;
*)
  echo "invalid EXPECT_CSP_MODE: $EXPECT_CSP_MODE" >&2
  exit 1
  ;;
esac

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
