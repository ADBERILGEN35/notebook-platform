#!/usr/bin/env bash
set -euo pipefail

failures=0

fail() {
  echo "FAIL: $1" >&2
  failures=$((failures + 1))
}

warn() {
  echo "WARN: $1" >&2
}

ok() {
  echo "OK: $1"
}

provider="${EMAIL_PROVIDER:-}"
from="${EMAIL_FROM:-${SMTP_FROM:-}}"
webhooks_enabled="${EMAIL_WEBHOOKS_ENABLED:-false}"
webhook_secret="${EMAIL_WEBHOOK_SECRET:-}"
provider_url="${EMAIL_PROVIDER_URL:-${EMAIL_GENERIC_HTTP_URL:-}}"
provider_api_key="${EMAIL_PROVIDER_API_KEY:-${EMAIL_GENERIC_HTTP_API_KEY:-}}"

if [[ -z "${provider}" ]]; then
  fail "EMAIL_PROVIDER is required"
elif [[ "${provider}" == "log" || "${provider}" == "noop" ]]; then
  fail "EMAIL_PROVIDER=${provider} is not production-ready"
else
  ok "provider is ${provider}"
fi

if [[ -z "${from}" || "${from}" != *@* ]]; then
  fail "EMAIL_FROM or SMTP_FROM must contain a production sender address"
else
  ok "sender address is configured"
fi

if [[ "${provider}" == "generic-http" || "${provider}" == "sendgrid" ]]; then
  if [[ -z "${provider_url}" ]]; then
    fail "EMAIL_PROVIDER_URL or EMAIL_GENERIC_HTTP_URL is required"
  else
    ok "provider URL is configured"
  fi
  if [[ -z "${provider_api_key}" ]]; then
    fail "EMAIL_PROVIDER_API_KEY or EMAIL_GENERIC_HTTP_API_KEY is required"
  elif [[ "${provider_api_key}" =~ ^(changeme|placeholder|example|dummy|test)$ ]]; then
    fail "provider API key looks like a placeholder"
  else
    ok "provider API key is present"
  fi
fi

if [[ "${webhooks_enabled}" == "true" ]]; then
  if [[ -z "${webhook_secret}" ]]; then
    fail "EMAIL_WEBHOOK_SECRET is required when EMAIL_WEBHOOKS_ENABLED=true"
  elif [[ "${webhook_secret}" =~ ^(changeme|placeholder|example|dummy|test)$ ]]; then
    fail "EMAIL_WEBHOOK_SECRET looks like a placeholder"
  else
    ok "webhook secret is present"
  fi
  if [[ "${EMAIL_WEBHOOK_REQUIRE_TIMESTAMP:-false}" != "true" ]]; then
    fail "EMAIL_WEBHOOK_REQUIRE_TIMESTAMP=true is required for production webhooks"
  fi
else
  warn "email webhooks are disabled"
fi

domain="${from#*@}"
if command -v dig >/dev/null 2>&1 && [[ -n "${domain}" && "${domain}" != "${from}" ]]; then
  dig TXT "${domain}" +short 2>/dev/null | grep -q "v=spf1" && ok "SPF TXT found" || warn "SPF TXT not found"
  dig TXT "_dmarc.${domain}" +short 2>/dev/null | grep -q "v=DMARC1" && ok "DMARC TXT found" || warn "DMARC TXT not found"
elif command -v nslookup >/dev/null 2>&1 && [[ -n "${domain}" && "${domain}" != "${from}" ]]; then
  nslookup -type=TXT "${domain}" >/dev/null 2>&1 && ok "TXT lookup available" || warn "TXT lookup failed"
else
  warn "dig/nslookup unavailable; skipping optional DNS checks"
fi

if [[ "${failures}" -gt 0 ]]; then
  echo "EMAIL_PROVIDER_READINESS_FAILED: ${failures} check(s) failed" >&2
  exit 1
fi

echo "Email provider readiness checks passed"
