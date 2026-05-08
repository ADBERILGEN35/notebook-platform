#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
SOURCE_LIST="${SOURCE_LIST:-identity,workspace,content}"
FORMAT="${FORMAT:-jsonl}"
LOOKBACK_HOURS="${LOOKBACK_HOURS:-24}"
CREATED_FROM="${CREATED_FROM:-}"
CREATED_TO="${CREATED_TO:-}"
OUTPUT_DIR="${OUTPUT_DIR:-./audit-archive}"
AUTH_MODE="${AUTH_MODE:-bearer}"
BEARER_TOKEN="${BEARER_TOKEN:-}"
ADMIN_COOKIE="${ADMIN_COOKIE:-}"
COMPRESS="${COMPRESS:-false}"
WRITE_MANIFEST="${WRITE_MANIFEST:-true}"
GENERATED_BY="${GENERATED_BY:-scheduled-audit-export}"
REDACTION_POLICY_VERSION="${REDACTION_POLICY_VERSION:-v1}"

if [[ "$FORMAT" != "jsonl" && "$FORMAT" != "csv" ]]; then
  echo "FORMAT must be jsonl or csv." >&2
  exit 1
fi

if [[ -z "$CREATED_FROM" || -z "$CREATED_TO" ]]; then
  CREATED_TO="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
  if date -u -d "-1 hour" +"%Y-%m-%dT%H:%M:%SZ" >/dev/null 2>&1; then
    CREATED_FROM="$(date -u -d "-${LOOKBACK_HOURS} hours" +"%Y-%m-%dT%H:%M:%SZ")"
  elif command -v python3 >/dev/null 2>&1; then
    CREATED_FROM="$(python3 - <<'PY'
from datetime import datetime, timedelta, timezone
import os
lookback = int(os.environ.get("LOOKBACK_HOURS", "24"))
print((datetime.now(timezone.utc).replace(microsecond=0) - timedelta(hours=lookback)).isoformat().replace("+00:00","Z"))
PY
)"
  else
    echo "Set CREATED_FROM and CREATED_TO explicitly on this platform." >&2
    exit 1
  fi
fi

if [[ "$AUTH_MODE" == "bearer" && -z "$BEARER_TOKEN" ]]; then
  echo "AUTH_MODE=bearer requires BEARER_TOKEN." >&2
  exit 1
fi
if [[ "$AUTH_MODE" == "cookie" && -z "$ADMIN_COOKIE" ]]; then
  echo "AUTH_MODE=cookie requires ADMIN_COOKIE." >&2
  exit 1
fi

mkdir -p "$OUTPUT_DIR"

sha256_file() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}

count_lines() {
  if [[ "$1" == *.gz ]]; then
    gzip -cd "$1" | wc -l | tr -d ' '
  else
    wc -l < "$1" | tr -d ' '
  fi
}

auth_headers=()
if [[ "$AUTH_MODE" == "bearer" ]]; then
  auth_headers=(-H "Authorization: Bearer ${BEARER_TOKEN}")
else
  auth_headers=(-H "Cookie: ${ADMIN_COOKIE}")
fi

IFS=',' read -r -a SOURCES <<< "$SOURCE_LIST"
for source in "${SOURCES[@]}"; do
  source="$(echo "$source" | sed 's/^ *//;s/ *$//')"
  [[ -z "$source" ]] && continue

  export_id="$(uuidgen | tr '[:upper:]' '[:lower:]')"
  base_name="audit-${source}-${CREATED_FROM//:/-}-${CREATED_TO//:/-}"
  data_file="${OUTPUT_DIR}/${base_name}.${FORMAT}"
  request_id="scheduled-export-${export_id}"
  headers_file="$(mktemp)"
  body_file="$(mktemp)"

  url="${BASE_URL}/admin/audit-events/export?source=${source}&format=${FORMAT}&createdFrom=${CREATED_FROM}&createdTo=${CREATED_TO}&sort=createdAt,asc"
  code="$(
    curl --silent --show-error --output "$body_file" --dump-header "$headers_file" --write-out "%{http_code}" \
      "${auth_headers[@]}" -H "X-Request-Id: ${request_id}" "$url"
  )"
  if [[ "$code" -lt 200 || "$code" -gt 299 ]]; then
    echo "Export failed source=${source} status=${code}" >&2
    rm -f "$headers_file" "$body_file"
    exit 1
  fi
  mv "$body_file" "$data_file"
  rm -f "$body_file"

  if [[ "$COMPRESS" == "true" ]]; then
    gzip -f "$data_file"
    data_file="${data_file}.gz"
  fi

  checksum="$(sha256_file "$data_file")"
  printf "%s  %s\n" "$checksum" "$(basename "$data_file")" > "${data_file}.sha256"
  record_count="$(count_lines "$data_file")"
  response_request_id="$(sed -n 's/^X-Request-Id:[[:space:]]*//Ip' "$headers_file" | tr -d '\r' | tail -n 1)"
  content_type="$(sed -n 's/^Content-Type:[[:space:]]*//Ip' "$headers_file" | tr -d '\r' | tail -n 1)"
  rm -f "$headers_file"

  if [[ "$WRITE_MANIFEST" == "true" ]]; then
    cat > "${OUTPUT_DIR}/${base_name}.manifest.json" <<EOF
{
  "exportId": "${export_id}",
  "source": "${source}",
  "format": "${FORMAT}",
  "createdFrom": "${CREATED_FROM}",
  "createdTo": "${CREATED_TO}",
  "recordCount": ${record_count},
  "generatedAt": "$(date -u +"%Y-%m-%dT%H:%M:%SZ")",
  "generatedBy": "${GENERATED_BY}",
  "authMode": "${AUTH_MODE}",
  "requestId": "${response_request_id:-$request_id}",
  "checksumSha256": "${checksum}",
  "fileName": "$(basename "$data_file")",
  "contentType": "${content_type}",
  "redactionPolicyVersion": "${REDACTION_POLICY_VERSION}",
  "schemaVersion": 1
}
EOF
  fi
done

echo "Scheduled audit export completed."
