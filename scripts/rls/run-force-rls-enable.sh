#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DB_URL="${DB_URL:-}"
DB_USER="${DB_USER:-}"
DB_PASSWORD="${DB_PASSWORD:-}"
CONFIRM_ENVIRONMENT="${CONFIRM_ENVIRONMENT:-}"
CONFIRM_FORCE_RLS="${CONFIRM_FORCE_RLS:-false}"

if [[ -z "$DB_URL" || -z "$DB_USER" || -z "$DB_PASSWORD" ]]; then
  echo "DB_URL, DB_USER and DB_PASSWORD are required." >&2
  exit 1
fi

if [[ "$CONFIRM_ENVIRONMENT" != "staging" && "$CONFIRM_FORCE_RLS" != "true" ]]; then
  echo "Refusing to enable FORCE RLS without CONFIRM_ENVIRONMENT=staging or CONFIRM_FORCE_RLS=true." >&2
  echo "Do not run this against production without a separately approved rollout window." >&2
  exit 1
fi

if ! command -v psql >/dev/null 2>&1; then
  echo "psql is required to enable FORCE RLS." >&2
  exit 1
fi

export PGPASSWORD="$DB_PASSWORD"

echo "Enabling workspace FORCE RLS tables."
psql "$DB_URL" -U "$DB_USER" -f "$ROOT_DIR/scripts/db/enable-force-rls-workspace.sql"

echo "Enabling content FORCE RLS tables."
psql "$DB_URL" -U "$DB_USER" -f "$ROOT_DIR/scripts/db/enable-force-rls-content.sql"
