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
MACHINE_JWT="${MACHINE_JWT:-}"
MACHINE_JWT_PRIVATE_KEY_PATH="${MACHINE_JWT_PRIVATE_KEY_PATH:-}"
MACHINE_JWT_ISSUER="${MACHINE_JWT_ISSUER:-https://audit-exporter.internal}"
MACHINE_JWT_SUBJECT="${MACHINE_JWT_SUBJECT:-audit-exporter}"
MACHINE_JWT_AUDIENCE="${MACHINE_JWT_AUDIENCE:-api-gateway}"
MACHINE_JWT_SCOPE="${MACHINE_JWT_SCOPE:-admin:audit:export}"
MACHINE_JWT_TTL_SECONDS="${MACHINE_JWT_TTL_SECONDS:-600}"
MACHINE_JWT_KID="${MACHINE_JWT_KID:-}"
COMPRESS="${COMPRESS:-false}"
WRITE_MANIFEST="${WRITE_MANIFEST:-true}"
GENERATED_BY="${GENERATED_BY:-scheduled-audit-export}"
REDACTION_POLICY_VERSION="${REDACTION_POLICY_VERSION:-v1}"
AUDIT_ARCHIVE_UPLOAD_ENABLED="${AUDIT_ARCHIVE_UPLOAD_ENABLED:-false}"
AUDIT_ARCHIVE_PROVIDER="${AUDIT_ARCHIVE_PROVIDER:-local}"
AUDIT_ARCHIVE_BUCKET="${AUDIT_ARCHIVE_BUCKET:-}"
AUDIT_ARCHIVE_PREFIX="${AUDIT_ARCHIVE_PREFIX:-audit-archive}"
AUDIT_ARCHIVE_FAIL_IF_EXISTS="${AUDIT_ARCHIVE_FAIL_IF_EXISTS:-true}"
AUDIT_ARCHIVE_UPLOAD_MAX_ATTEMPTS="${AUDIT_ARCHIVE_UPLOAD_MAX_ATTEMPTS:-3}"
AUDIT_ARCHIVE_UPLOAD_RETRY_SECONDS="${AUDIT_ARCHIVE_UPLOAD_RETRY_SECONDS:-10}"
AWS_REGION="${AWS_REGION:-}"
AWS_ENDPOINT_URL="${AWS_ENDPOINT_URL:-}"
AUDIT_ARCHIVE_S3_STORAGE_CLASS="${AUDIT_ARCHIVE_S3_STORAGE_CLASS:-}"
AUDIT_ARCHIVE_S3_SERVER_SIDE_ENCRYPTION="${AUDIT_ARCHIVE_S3_SERVER_SIDE_ENCRYPTION:-}"
AUDIT_ARCHIVE_S3_SSE_KMS_KEY_ID="${AUDIT_ARCHIVE_S3_SSE_KMS_KEY_ID:-}"
AUDIT_ARCHIVE_S3_OBJECT_LOCK_MODE="${AUDIT_ARCHIVE_S3_OBJECT_LOCK_MODE:-}"
AUDIT_ARCHIVE_S3_OBJECT_LOCK_RETAIN_UNTIL_DATE="${AUDIT_ARCHIVE_S3_OBJECT_LOCK_RETAIN_UNTIL_DATE:-}"
AUDIT_ARCHIVE_S3_OBJECT_LOCK_LEGAL_HOLD="${AUDIT_ARCHIVE_S3_OBJECT_LOCK_LEGAL_HOLD:-}"

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
if [[ "$AUTH_MODE" == "machine" && -z "$MACHINE_JWT" && -z "$MACHINE_JWT_PRIVATE_KEY_PATH" ]]; then
  echo "AUTH_MODE=machine requires MACHINE_JWT or MACHINE_JWT_PRIVATE_KEY_PATH." >&2
  exit 1
fi
if [[ "$AUDIT_ARCHIVE_UPLOAD_ENABLED" == "true" && -z "$AUDIT_ARCHIVE_BUCKET" ]]; then
  echo "AUDIT_ARCHIVE_BUCKET is required when upload is enabled." >&2
  exit 1
fi
if [[ "$AUDIT_ARCHIVE_UPLOAD_ENABLED" == "true" && "$AUDIT_ARCHIVE_PROVIDER" != "s3-compatible" && "$AUDIT_ARCHIVE_PROVIDER" != "local" ]]; then
  echo "AUDIT_ARCHIVE_PROVIDER must be local or s3-compatible." >&2
  exit 1
fi
if [[ "$AUDIT_ARCHIVE_UPLOAD_ENABLED" == "true" && "$AUDIT_ARCHIVE_PROVIDER" == "s3-compatible" ]]; then
  if ! command -v aws >/dev/null 2>&1; then
    echo "aws cli is required for s3-compatible uploads." >&2
    exit 1
  fi
  if [[ -z "$AWS_REGION" ]]; then
    echo "AWS_REGION is required for s3-compatible uploads." >&2
    exit 1
  fi
fi
if [[ -n "$AUDIT_ARCHIVE_S3_OBJECT_LOCK_MODE" && -z "$AUDIT_ARCHIVE_S3_OBJECT_LOCK_RETAIN_UNTIL_DATE" ]]; then
  echo "AUDIT_ARCHIVE_S3_OBJECT_LOCK_RETAIN_UNTIL_DATE is required when object lock mode is set." >&2
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

iso_path() {
  local value="$1"
  echo "$value" | sed 's/[:]/-/g'
}

json_escape() {
  printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'
}

aws_common_args=()
if [[ -n "$AWS_ENDPOINT_URL" ]]; then
  aws_common_args+=(--endpoint-url "$AWS_ENDPOINT_URL")
fi

aws_head_exists() {
  local key="$1"
  aws "${aws_common_args[@]}" s3api head-object --bucket "$AUDIT_ARCHIVE_BUCKET" --key "$key" >/dev/null 2>&1
}

aws_put_with_retry() {
  local file_path="$1"
  local object_key="$2"
  local content_type="$3"
  local metadata="$4"
  local attempts=1
  while [[ "$attempts" -le "$AUDIT_ARCHIVE_UPLOAD_MAX_ATTEMPTS" ]]; do
    if [[ "$AUDIT_ARCHIVE_FAIL_IF_EXISTS" == "true" ]] && aws_head_exists "$object_key"; then
      echo "Object already exists and overwrite is disabled: ${object_key}" >&2
      return 1
    fi
    put_args=(s3api put-object --bucket "$AUDIT_ARCHIVE_BUCKET" --key "$object_key" --body "$file_path" --content-type "$content_type" --metadata "$metadata")
    if [[ -n "$AUDIT_ARCHIVE_S3_STORAGE_CLASS" ]]; then
      put_args+=(--storage-class "$AUDIT_ARCHIVE_S3_STORAGE_CLASS")
    fi
    if [[ -n "$AUDIT_ARCHIVE_S3_SERVER_SIDE_ENCRYPTION" ]]; then
      put_args+=(--server-side-encryption "$AUDIT_ARCHIVE_S3_SERVER_SIDE_ENCRYPTION")
    fi
    if [[ -n "$AUDIT_ARCHIVE_S3_SSE_KMS_KEY_ID" ]]; then
      put_args+=(--ssekms-key-id "$AUDIT_ARCHIVE_S3_SSE_KMS_KEY_ID")
    fi
    if [[ -n "$AUDIT_ARCHIVE_S3_OBJECT_LOCK_MODE" ]]; then
      put_args+=(--object-lock-mode "$AUDIT_ARCHIVE_S3_OBJECT_LOCK_MODE")
      put_args+=(--object-lock-retain-until-date "$AUDIT_ARCHIVE_S3_OBJECT_LOCK_RETAIN_UNTIL_DATE")
    fi
    if [[ -n "$AUDIT_ARCHIVE_S3_OBJECT_LOCK_LEGAL_HOLD" ]]; then
      put_args+=(--object-lock-legal-hold-status "$AUDIT_ARCHIVE_S3_OBJECT_LOCK_LEGAL_HOLD")
    fi

    if aws "${aws_common_args[@]}" "${put_args[@]}" >/dev/null; then
      return 0
    fi
    if [[ "$attempts" -ge "$AUDIT_ARCHIVE_UPLOAD_MAX_ATTEMPTS" ]]; then
      return 1
    fi
    sleep "$AUDIT_ARCHIVE_UPLOAD_RETRY_SECONDS"
    attempts=$((attempts + 1))
  done
  return 1
}

auth_headers=()
if [[ "$AUTH_MODE" == "bearer" ]]; then
  auth_headers=(-H "Authorization: Bearer ${BEARER_TOKEN}")
elif [[ "$AUTH_MODE" == "machine" ]]; then
  if [[ -z "$MACHINE_JWT" ]]; then
    script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    MACHINE_JWT="$(
      MACHINE_JWT_PRIVATE_KEY_PATH="$MACHINE_JWT_PRIVATE_KEY_PATH" \
      MACHINE_JWT_ISSUER="$MACHINE_JWT_ISSUER" \
      MACHINE_JWT_SUBJECT="$MACHINE_JWT_SUBJECT" \
      MACHINE_JWT_AUDIENCE="$MACHINE_JWT_AUDIENCE" \
      MACHINE_JWT_SCOPE="$MACHINE_JWT_SCOPE" \
      MACHINE_JWT_TTL_SECONDS="$MACHINE_JWT_TTL_SECONDS" \
      MACHINE_JWT_KID="$MACHINE_JWT_KID" \
      "${script_dir}/generate-machine-jwt.sh"
    )"
  fi
  auth_headers=(-H "Authorization: Bearer ${MACHINE_JWT}")
  if [[ "$GENERATED_BY" == "scheduled-audit-export" ]]; then
    GENERATED_BY="machine:${MACHINE_JWT_SUBJECT}"
  fi
else
  auth_headers=(-H "Cookie: ${ADMIN_COOKIE}")
fi

IFS=',' read -r -a SOURCES <<< "$SOURCE_LIST"
for source in "${SOURCES[@]}"; do
  source="$(echo "$source" | sed 's/^ *//;s/ *$//')"
  [[ -z "$source" ]] && continue

  export_id="$(uuidgen | tr '[:upper:]' '[:lower:]')"
  from_safe="$(iso_path "$CREATED_FROM")"
  to_safe="$(iso_path "$CREATED_TO")"
  base_name="audit-${source}-${from_safe}_${to_safe}"
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
    archive_provider="$AUDIT_ARCHIVE_PROVIDER"
    archive_upload_status="NOT_UPLOADED"
    object_key=""
    checksum_object_key=""
    manifest_object_key=""
    uploaded_at=""
    worm_mode="none"
    retention_until=""
    if [[ -n "$AUDIT_ARCHIVE_S3_OBJECT_LOCK_MODE" ]]; then
      worm_mode="$(echo "$AUDIT_ARCHIVE_S3_OBJECT_LOCK_MODE" | tr '[:upper:]' '[:lower:]')"
      retention_until="$AUDIT_ARCHIVE_S3_OBJECT_LOCK_RETAIN_UNTIL_DATE"
    fi
    year="$(echo "$CREATED_FROM" | cut -c1-4)"
    month="$(echo "$CREATED_FROM" | cut -c6-7)"
    day="$(echo "$CREATED_FROM" | cut -c9-10)"
    object_prefix="${AUDIT_ARCHIVE_PREFIX}/source=${source}/year=${year}/month=${month}/day=${day}"
    object_key="${object_prefix}/$(basename "$data_file")"
    checksum_object_key="${object_prefix}/$(basename "${data_file}.sha256")"
    manifest_object_key="${object_prefix}/${base_name}.manifest.json"

    metadata="source=${source},created_from=$(json_escape "$CREATED_FROM"),created_to=$(json_escape "$CREATED_TO"),record_count=${record_count},checksum_sha256=${checksum},export_id=${export_id},schema_version=1,redaction_policy_version=${REDACTION_POLICY_VERSION}"

    if [[ "$AUDIT_ARCHIVE_UPLOAD_ENABLED" == "true" && "$AUDIT_ARCHIVE_PROVIDER" == "s3-compatible" ]]; then
      if aws_put_with_retry "$data_file" "$object_key" "$content_type" "$metadata"; then
        if aws_put_with_retry "${data_file}.sha256" "$checksum_object_key" "text/plain" "$metadata"; then
          archive_upload_status="UPLOADED"
          uploaded_at="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
        else
          archive_upload_status="FAILED"
        fi
      else
        archive_upload_status="FAILED"
      fi
      if [[ "$archive_upload_status" != "UPLOADED" ]]; then
        echo "Upload failed for source=${source}" >&2
        exit 1
      fi
    fi

    manifest_path="${OUTPUT_DIR}/${base_name}.manifest.json"
    cat > "$manifest_path" <<EOF
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
  "schemaVersion": 1,
  "archiveProvider": "${archive_provider}",
  "bucket": "$(json_escape "$AUDIT_ARCHIVE_BUCKET")",
  "objectKey": "$(json_escape "$object_key")",
  "manifestObjectKey": "$(json_escape "$manifest_object_key")",
  "checksumObjectKey": "$(json_escape "$checksum_object_key")",
  "uploadedAt": "$(json_escape "$uploaded_at")",
  "uploadStatus": "${archive_upload_status}",
  "wormMode": "${worm_mode}",
  "retentionUntil": "$(json_escape "$retention_until")"
}
EOF
    if [[ "$AUDIT_ARCHIVE_UPLOAD_ENABLED" == "true" && "$AUDIT_ARCHIVE_PROVIDER" == "s3-compatible" ]]; then
      if ! aws_put_with_retry "$manifest_path" "$manifest_object_key" "application/json" "$metadata"; then
        echo "Manifest upload failed for source=${source}" >&2
        exit 1
      fi
    fi
  fi
done

echo "Scheduled audit export completed."
