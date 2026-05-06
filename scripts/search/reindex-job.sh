#!/usr/bin/env bash
set -euo pipefail

SEARCH_SERVICE_URL="${SEARCH_SERVICE_URL:-http://localhost:8085}"
SERVICE_JWT="${SERVICE_JWT:-}"
MODE="${MODE:-FULL}"
WORKSPACE_ID="${WORKSPACE_ID:-}"
NOTEBOOK_ID="${NOTEBOOK_ID:-}"
CLEANUP_ORPHANS="${CLEANUP_ORPHANS:-false}"
DRY_RUN_CLEANUP="${DRY_RUN_CLEANUP:-false}"
SHOW_ORPHAN_PREVIEW="${SHOW_ORPHAN_PREVIEW:-false}"
ORPHAN_PREVIEW_SIZE="${ORPHAN_PREVIEW_SIZE:-20}"
TIMEOUT_SECONDS="${TIMEOUT_SECONDS:-600}"
POLL_SECONDS="${POLL_SECONDS:-5}"

if [[ -z "$SERVICE_JWT" ]]; then
  echo "SERVICE_JWT is required." >&2
  exit 1
fi

payload="{\"mode\":\"${MODE}\",\"cleanupOrphans\":${CLEANUP_ORPHANS},\"dryRunCleanup\":${DRY_RUN_CLEANUP}"
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
  preview_count="$(printf '%s' "$status_response" | sed -n 's/.*"cleanupPreviewCount"[[:space:]]*:[[:space:]]*\([0-9][0-9]*\).*/\1/p')"
  cleanup_executed="$(printf '%s' "$status_response" | sed -n 's/.*"cleanupOrphansExecuted"[[:space:]]*:[[:space:]]*\(true\|false\).*/\1/p')"
  if [[ -n "$archived" ]]; then
    echo "archived_orphans=${archived} cleanup_preview_count=${preview_count:-unknown} cleanup_executed=${cleanup_executed:-unknown}" >&2
  fi

  if printf '%s' "$status_response" | grep -Eq '"status"[[:space:]]*:[[:space:]]*"(COMPLETED|FAILED|CANCELLED)"'; then
    if printf '%s' "$status_response" | grep -q '"status"[[:space:]]*:[[:space:]]*"COMPLETED"'; then
      if [[ "$SHOW_ORPHAN_PREVIEW" == "true" ]]; then
        curl -fsS \
          -H "X-Service-Authorization: Bearer ${SERVICE_JWT}" \
          "${SEARCH_SERVICE_URL}/internal/search/reindex-jobs/${job_id}/orphan-preview?size=${ORPHAN_PREVIEW_SIZE}"
      fi
      exit 0
    fi
    exit 1
  fi
  sleep "$POLL_SECONDS"
done

echo "Timed out waiting for reindex job ${job_id}." >&2
exit 1
