#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
ACCESS_TOKEN="${ACCESS_TOKEN:-}"
WORKSPACE_ID="${WORKSPACE_ID:-}"
QUERY="${QUERY:-roadmap}"

if [[ -z "$ACCESS_TOKEN" || -z "$WORKSPACE_ID" ]]; then
  echo "ACCESS_TOKEN and WORKSPACE_ID are required." >&2
  echo "Example: ACCESS_TOKEN=... WORKSPACE_ID=... $0" >&2
  exit 1
fi

response="$(
  curl -fsS \
    -H "Authorization: Bearer ${ACCESS_TOKEN}" \
    -H "X-Workspace-Id: ${WORKSPACE_ID}" \
    "${BASE_URL}/search/notes?workspaceId=${WORKSPACE_ID}&q=${QUERY}&page=0&size=20"
)"

echo "$response"

if ! printf '%s' "$response" | grep -q '"items"'; then
  echo "Search smoke test did not receive PageResponse items." >&2
  exit 1
fi
