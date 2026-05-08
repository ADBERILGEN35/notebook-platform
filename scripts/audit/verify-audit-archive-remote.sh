#!/usr/bin/env bash
set -euo pipefail

AUDIT_ARCHIVE_PROVIDER="${AUDIT_ARCHIVE_PROVIDER:-s3-compatible}"
AUDIT_ARCHIVE_BUCKET="${AUDIT_ARCHIVE_BUCKET:-}"
AUDIT_ARCHIVE_OBJECT_KEY="${AUDIT_ARCHIVE_OBJECT_KEY:-}"
AUDIT_ARCHIVE_MANIFEST_KEY="${AUDIT_ARCHIVE_MANIFEST_KEY:-}"
AWS_REGION="${AWS_REGION:-}"
AWS_ENDPOINT_URL="${AWS_ENDPOINT_URL:-}"
DOWNLOAD_AND_VERIFY_MANIFEST="${DOWNLOAD_AND_VERIFY_MANIFEST:-false}"

if [[ "$AUDIT_ARCHIVE_PROVIDER" != "s3-compatible" ]]; then
  echo "Only s3-compatible remote verification is implemented in this phase." >&2
  exit 1
fi
if [[ -z "$AUDIT_ARCHIVE_BUCKET" || -z "$AUDIT_ARCHIVE_OBJECT_KEY" || -z "$AUDIT_ARCHIVE_MANIFEST_KEY" ]]; then
  echo "AUDIT_ARCHIVE_BUCKET, AUDIT_ARCHIVE_OBJECT_KEY and AUDIT_ARCHIVE_MANIFEST_KEY are required." >&2
  exit 1
fi
if [[ -z "$AWS_REGION" ]]; then
  echo "AWS_REGION is required." >&2
  exit 1
fi
if ! command -v aws >/dev/null 2>&1; then
  echo "aws cli is required for remote verification." >&2
  exit 1
fi

aws_common_args=()
if [[ -n "$AWS_ENDPOINT_URL" ]]; then
  aws_common_args+=(--endpoint-url "$AWS_ENDPOINT_URL")
fi

aws "${aws_common_args[@]}" s3api head-object --bucket "$AUDIT_ARCHIVE_BUCKET" --key "$AUDIT_ARCHIVE_OBJECT_KEY" >/dev/null
aws "${aws_common_args[@]}" s3api head-object --bucket "$AUDIT_ARCHIVE_BUCKET" --key "$AUDIT_ARCHIVE_MANIFEST_KEY" >/dev/null

if [[ "$DOWNLOAD_AND_VERIFY_MANIFEST" == "true" ]]; then
  tmp_dir="$(mktemp -d)"
  trap 'rm -rf "$tmp_dir"' EXIT
  data_name="$(basename "$AUDIT_ARCHIVE_OBJECT_KEY")"
  manifest_name="$(basename "$AUDIT_ARCHIVE_MANIFEST_KEY")"
  aws "${aws_common_args[@]}" s3 cp "s3://${AUDIT_ARCHIVE_BUCKET}/${AUDIT_ARCHIVE_OBJECT_KEY}" "${tmp_dir}/${data_name}" >/dev/null
  aws "${aws_common_args[@]}" s3 cp "s3://${AUDIT_ARCHIVE_BUCKET}/${AUDIT_ARCHIVE_MANIFEST_KEY}" "${tmp_dir}/${manifest_name}" >/dev/null
  FILE="${tmp_dir}/${data_name}" MANIFEST="${tmp_dir}/${manifest_name}" bash "$(dirname "${BASH_SOURCE[0]}")/verify-audit-archive.sh"
fi

echo "Remote archive verification successful."
