#!/usr/bin/env bash
set -euo pipefail

OPENSEARCH_URL="${OPENSEARCH_URL:?OPENSEARCH_URL is required}"
OPENSEARCH_INDEX_NOTES="${OPENSEARCH_INDEX_NOTES:-notebook-notes}"

auth_args=()
if [[ -n "${OPENSEARCH_USERNAME:-}" ]]; then
  auth_args=(-u "${OPENSEARCH_USERNAME}:${OPENSEARCH_PASSWORD:-}")
fi

curl -fsS "${auth_args[@]}" "${OPENSEARCH_URL%/}/_cluster/health"
printf '\n'
curl -fsS "${auth_args[@]}" "${OPENSEARCH_URL%/}/${OPENSEARCH_INDEX_NOTES}/_count"
printf '\n'
