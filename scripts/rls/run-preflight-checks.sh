#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DB_URL="${DB_URL:-}"
DB_USER="${DB_USER:-}"
DB_PASSWORD="${DB_PASSWORD:-}"
WORKSPACE_RUNTIME_ROLE="${WORKSPACE_RUNTIME_ROLE:-notebook_workspace_runtime}"
CONTENT_RUNTIME_ROLE="${CONTENT_RUNTIME_ROLE:-notebook_content_runtime}"

if [[ -z "$DB_URL" || -z "$DB_USER" || -z "$DB_PASSWORD" ]]; then
  echo "DB_URL, DB_USER and DB_PASSWORD are required." >&2
  exit 1
fi

if ! command -v psql >/dev/null 2>&1; then
  echo "psql is required for RLS preflight checks." >&2
  exit 1
fi

export PGPASSWORD="$DB_PASSWORD"

echo "== DB role permissions =="
psql "$DB_URL" \
  -U "$DB_USER" \
  -v workspace_runtime_role="$WORKSPACE_RUNTIME_ROLE" \
  -v content_runtime_role="$CONTENT_RUNTIME_ROLE" \
  -f "$ROOT_DIR/scripts/rls/check-db-role-permissions.sql"

echo "== RLS status =="
psql "$DB_URL" -U "$DB_USER" -f "$ROOT_DIR/scripts/rls/check-rls-status.sql"

echo "== Runtime SET LOCAL workspace setting =="
psql "$DB_URL" -U "$DB_USER" -f "$ROOT_DIR/scripts/rls/check-current-workspace-setting.sql"
