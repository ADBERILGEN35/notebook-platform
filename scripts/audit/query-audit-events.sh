#!/usr/bin/env bash
set -euo pipefail

SERVICE="${SERVICE:-workspace}"
SERVICE_JWT="${SERVICE_JWT:-}"
EVENT_TYPE="${EVENT_TYPE:-}"
WORKSPACE_ID="${WORKSPACE_ID:-}"
BASE_URL="${BASE_URL:-}"

case "$SERVICE" in
  identity) DEFAULT_URL="http://localhost:8081" ;;
  workspace) DEFAULT_URL="http://localhost:8082" ;;
  content) DEFAULT_URL="http://localhost:8083" ;;
  *)
    echo "SERVICE must be identity, workspace or content" >&2
    exit 1
    ;;
esac

if [[ -z "$SERVICE_JWT" ]]; then
  echo "SERVICE_JWT is required. Generate a service JWT with scope internal:audit:read." >&2
  exit 1
fi

BASE_URL="${BASE_URL:-$DEFAULT_URL}"
query=()
if [[ -n "$EVENT_TYPE" ]]; then
  query+=("eventType=$EVENT_TYPE")
fi
if [[ -n "$WORKSPACE_ID" ]]; then
  query+=("workspaceId=$WORKSPACE_ID")
fi

path="/internal/audit-events"
if [[ "${#query[@]}" -gt 0 ]]; then
  joined="$(IFS='&'; echo "${query[*]}")"
  path="$path?$joined"
fi

curl -sS "$BASE_URL$path" \
  -H "X-Service-Authorization: Bearer $SERVICE_JWT"
