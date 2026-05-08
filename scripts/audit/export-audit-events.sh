#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
SOURCE="${SOURCE:-identity}"
FORMAT="${FORMAT:-jsonl}"
CREATED_FROM="${CREATED_FROM:-}"
CREATED_TO="${CREATED_TO:-}"
OUTPUT_FILE="${OUTPUT_FILE:-audit-export.${FORMAT}}"
ADMIN_COOKIE="${ADMIN_COOKIE:-}"
BEARER_TOKEN="${BEARER_TOKEN:-}"

if [[ -z "$CREATED_FROM" || -z "$CREATED_TO" ]]; then
  echo "CREATED_FROM and CREATED_TO are required (ISO-8601)." >&2
  exit 1
fi

if [[ "$FORMAT" != "csv" && "$FORMAT" != "jsonl" ]]; then
  echo "FORMAT must be csv or jsonl." >&2
  exit 1
fi

AUTH_ARGS=()
if [[ -n "$BEARER_TOKEN" ]]; then
  AUTH_ARGS=(-H "Authorization: Bearer ${BEARER_TOKEN}")
elif [[ -n "$ADMIN_COOKIE" ]]; then
  AUTH_ARGS=(-H "Cookie: ${ADMIN_COOKIE}")
else
  echo "Provide BEARER_TOKEN or ADMIN_COOKIE." >&2
  exit 1
fi

URL="${BASE_URL}/admin/audit-events/export?source=${SOURCE}&format=${FORMAT}&createdFrom=${CREATED_FROM}&createdTo=${CREATED_TO}&sort=createdAt,desc"

echo "Exporting audit events: source=${SOURCE} format=${FORMAT} output=${OUTPUT_FILE}"
curl --fail --silent --show-error "${AUTH_ARGS[@]}" "$URL" --output "$OUTPUT_FILE"
echo "Done: ${OUTPUT_FILE}"
