#!/usr/bin/env bash
set -euo pipefail

SEARCH_SERVICE_URL="${SEARCH_SERVICE_URL:-http://localhost:8085}"
SERVICE_JWT="${SERVICE_JWT:-}"
MODE="${MODE:-FULL}"
WORKSPACE_ID="${WORKSPACE_ID:-}"
NOTEBOOK_ID="${NOTEBOOK_ID:-}"
CLEANUP_ORPHANS="${CLEANUP_ORPHANS:-false}"
TIMEOUT_SECONDS="${TIMEOUT_SECONDS:-600}"
POLL_SECONDS="${POLL_SECONDS:-5}"

if [[ -z "$SERVICE_JWT" ]]; then
  echo "SERVICE_JWT is required." >&2
  exit 1
fi

payload="{\"mode\":\"${MODE}\",\"cleanupOrphans\":${CLEANUP_ORPHANS}}"
if [[ -n "$WORKSPACE_ID" ]]; then
  payload="${payload},\"workspaceId\":\"${WORKSPACE_ID}\""
fi
if [[ -n "$NOTEBOOK_ID" ]]; then
  payload="${payload},\"notebookId\":\"${NOTEBOOK_ID}\""
fi
payload="${payload}}"

create_response="$(
  curl -fsS \
    -H "Content-Type: application/json" \
    -H "X-Service-Authorization: Bearer ${SERVICE_JWT}" \
    -d "$payload" \
    "${SEARCH_SERVICE_URL}/internal/search/reindex-jobs"
)"

echo "$create_response"
job_id="$(printf '%s' "$create_response" | sed -n 's/.*"jobId"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')"
if [[ -z "$job_id" ]]; then
  echo "Could not parse jobId from create response." >&2
  exit 1
fi

deadline=$((SECONDS + TIMEOUT_SECONDS))
while (( SECONDS < deadline )); do
  status_response="$(
    curl -fsS \
      -H "X-Service-Authorization: Bearer ${SERVICE_JWT}" \
      "${SEARCH_SERVICE_URL}/internal/search/reindex-jobs/${job_id}"
  )"
  echo "$status_response"
  archived="$(printf '%s' "$status_response" | sed -n 's/.*"totalArchivedOrphans"[[:space:]]*:[[:space:]]*\([0-9][0-9]*\).*/\1/p')"
  cleanup_executed="$(printf '%s' "$status_response" | sed -n 's/.*"cleanupOrphansExecuted"[[:space:]]*:[[:space:]]*\(true\|false\).*/\1/p')"
  if [[ -n "$archived" ]]; then
    echo "archived_orphans=${archived} cleanup_executed=${cleanup_executed:-unknown}" >&2
  fi

  if printf '%s' "$status_response" | grep -Eq '"status"[[:space:]]*:[[:space:]]*"(COMPLETED|FAILED|CANCELLED)"'; then
    printf '%s' "$status_response" | grep -q '"status"[[:space:]]*:[[:space:]]*"COMPLETED"'
    exit $?
  fi
  sleep "$POLL_SECONDS"
done

echo "Timed out waiting for reindex job ${job_id}." >&2
exit 1
