#!/bin/sh
set -eu

mkdir -p /tmp

cat > /tmp/runtime-config.js <<EOF
window.__NOTEBOOK_CONFIG__ = {
  API_BASE_URL: "${FRONTEND_API_BASE_URL:-}",
  AUTH_TRANSPORT: "${FRONTEND_AUTH_TRANSPORT:-bearer}",
  ADMIN_UI_ENABLED: "${FRONTEND_ADMIN_UI_ENABLED:-false}",
  ADMIN_UI_DEV_OPEN: "${FRONTEND_ADMIN_UI_DEV_OPEN:-false}",
  AUDIT_API_MODE: "${FRONTEND_AUDIT_API_MODE:-mock}"
}
EOF

exec "$@"
