#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8084}"
SERVICE_JWT="${SERVICE_JWT:-}"
RECIPIENT_EMAIL="${RECIPIENT_EMAIL:-security@example.com}"
WEBHOOK_SECRET="${EMAIL_WEBHOOK_SECRET:-}"
PROVIDER="${EMAIL_WEBHOOK_PROVIDER:-generic-http}"

if [[ -z "${SERVICE_JWT}" ]]; then
  echo "SERVICE_JWT is required. Provide a service JWT with audience=notification-service and scope=internal:notification:email:send." >&2
  exit 2
fi

curl -fsS \
  -H "Content-Type: application/json" \
  -H "X-Service-Authorization: Bearer ${SERVICE_JWT}" \
  -d "{
    \"type\":\"WORKSPACE_INVITATION\",
    \"recipientEmail\":\"${RECIPIENT_EMAIL}\",
    \"subject\":\"Workspace invitation\",
    \"templateKey\":\"workspace-invitation\",
    \"templateVariables\":{
      \"workspaceName\":\"smoke-test\",
      \"inviterEmail\":\"owner@example.com\",
      \"role\":\"ADMIN\",
      \"acceptUrl\":\"https://example.test/invitations/accept?token=redacted\"
    },
    \"idempotencyKey\":\"smoke-test:notification:${RECIPIENT_EMAIL}\"
  }" \
  "${BASE_URL}/internal/notifications/email"

echo

if [[ -n "${WEBHOOK_SECRET}" ]]; then
  timestamp="$(date +%s)"
  delivered_body='{"event":"delivered","messageId":"smoke-provider-message","eventId":"smoke-delivered","email":"'"${RECIPIENT_EMAIL}"'","timestamp":'"${timestamp}"'}'
  delivered_sig="$(printf '%s.%s' "${timestamp}" "${delivered_body}" | openssl dgst -sha256 -hmac "${WEBHOOK_SECRET}" -hex | awk '{print $2}')"
  curl -fsS \
    -H "Content-Type: application/json" \
    -H "X-Email-Timestamp: ${timestamp}" \
    -H "X-Email-Signature: sha256=${delivered_sig}" \
    -d "${delivered_body}" \
    "${BASE_URL}/webhooks/email/${PROVIDER}"
  echo

  bounce_body='{"event":"bounce","messageId":"smoke-provider-message","eventId":"smoke-bounce","email":"'"${RECIPIENT_EMAIL}"'","timestamp":'"${timestamp}"'}'
  bounce_sig="$(printf '%s.%s' "${timestamp}" "${bounce_body}" | openssl dgst -sha256 -hmac "${WEBHOOK_SECRET}" -hex | awk '{print $2}')"
  curl -fsS \
    -H "Content-Type: application/json" \
    -H "X-Email-Timestamp: ${timestamp}" \
    -H "X-Email-Signature: sha256=${bounce_sig}" \
    -d "${bounce_body}" \
    "${BASE_URL}/webhooks/email/${PROVIDER}"
  echo
else
  echo "EMAIL_WEBHOOK_SECRET not set; skipping webhook simulation."
fi
