#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8084}"
SERVICE_JWT="${SERVICE_JWT:-}"
RECIPIENT_EMAIL="${RECIPIENT_EMAIL:-security@example.com}"

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
