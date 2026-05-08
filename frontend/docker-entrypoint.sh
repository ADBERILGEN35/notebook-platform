#!/bin/sh
set -eu

mkdir -p /tmp

extract_origin() {
  value="$1"
  if [ -z "$value" ]; then
    return 0
  fi
  printf '%s' "$value" | sed -E 's#^(https?://[^/]+).*$#\1#'
}

append_sources() {
  base="$1"
  extras="$2"
  if [ -n "$extras" ]; then
    for src in $(printf '%s' "$extras" | tr ',' ' '); do
      trimmed="$(printf '%s' "$src" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')"
      if [ -n "$trimmed" ]; then
        base="$base $trimmed"
      fi
    done
  fi
  printf '%s' "$base"
}

api_origin="$(extract_origin "${FRONTEND_API_BASE_URL:-}")"
connect_src="'self'"
if [ -n "$api_origin" ]; then
  connect_src="$connect_src $api_origin"
fi
connect_src="$(append_sources "$connect_src" "${FRONTEND_CSP_CONNECT_SRC:-}")"
img_src="$(append_sources "'self' data: blob:" "${FRONTEND_CSP_IMG_SRC:-}")"
font_src="$(append_sources "'self' data:" "${FRONTEND_CSP_FONT_SRC:-}")"

csp_policy="default-src 'self'; base-uri 'self'; object-src 'none'; frame-ancestors 'none'; form-action 'self'; img-src $img_src; font-src $font_src; style-src 'self' 'unsafe-inline'; script-src 'self'; connect-src $connect_src; worker-src 'self' blob:; manifest-src 'self'"
if [ -n "${FRONTEND_CSP_REPORT_URI:-}" ]; then
  csp_policy="$csp_policy; report-uri ${FRONTEND_CSP_REPORT_URI}"
fi

cat > /tmp/security-headers.conf <<EOF
add_header X-Content-Type-Options "nosniff" always;
add_header X-Frame-Options "DENY" always;
add_header Referrer-Policy "strict-origin-when-cross-origin" always;
add_header Permissions-Policy "geolocation=(), camera=(), microphone=()" always;
add_header Cross-Origin-Opener-Policy "same-origin" always;
add_header Cross-Origin-Resource-Policy "same-origin" always;
EOF

if [ "${FRONTEND_CSP_ENABLED:-false}" = "true" ]; then
  if [ "${FRONTEND_CSP_REPORT_ONLY:-false}" = "true" ]; then
    printf 'add_header Content-Security-Policy-Report-Only "%s" always;\n' "$csp_policy" >> /tmp/security-headers.conf
  else
    printf 'add_header Content-Security-Policy "%s" always;\n' "$csp_policy" >> /tmp/security-headers.conf
  fi
fi

cat > /tmp/runtime-config.js <<EOF
window.__NOTEBOOK_CONFIG__ = {
  API_BASE_URL: "${FRONTEND_API_BASE_URL:-}",
  AUTH_TRANSPORT: "${FRONTEND_AUTH_TRANSPORT:-bearer}",
  SSO_ENABLED: "${FRONTEND_SSO_ENABLED:-false}",
  PWA_ENABLED: "${FRONTEND_PWA_ENABLED:-true}",
  OFFLINE_NOTES_ENABLED: "${FRONTEND_OFFLINE_NOTES_ENABLED:-true}",
  OFFLINE_NOTES_MAX_ITEMS: "${FRONTEND_OFFLINE_NOTES_MAX_ITEMS:-50}",
  OFFLINE_EDIT_ENABLED: "${FRONTEND_OFFLINE_EDIT_ENABLED:-false}",
  OFFLINE_SYNC_ENABLED: "${FRONTEND_OFFLINE_SYNC_ENABLED:-false}",
  OFFLINE_EDIT_MAX_DRAFTS: "${FRONTEND_OFFLINE_EDIT_MAX_DRAFTS:-50}",
  OFFLINE_EDIT_MAX_DRAFT_AGE_DAYS: "${FRONTEND_OFFLINE_EDIT_MAX_DRAFT_AGE_DAYS:-7}",
  NOTIFICATIONS_ENABLED: "${FRONTEND_NOTIFICATIONS_ENABLED:-false}",
  NOTIFICATIONS_SSE_ENABLED: "${FRONTEND_NOTIFICATIONS_SSE_ENABLED:-false}",
  NOTIFICATION_PREFERENCES_ENABLED: "${FRONTEND_NOTIFICATION_PREFERENCES_ENABLED:-false}",
  WORKSPACE_NOTIFICATION_PREFERENCES_ENABLED: "${FRONTEND_WORKSPACE_NOTIFICATION_PREFERENCES_ENABLED:-false}",
  MFA_UI_ENABLED: "${FRONTEND_MFA_UI_ENABLED:-false}",
  ADMIN_UI_ENABLED: "${FRONTEND_ADMIN_UI_ENABLED:-false}",
  ADMIN_UI_DEV_OPEN: "${FRONTEND_ADMIN_UI_DEV_OPEN:-false}",
  AUDIT_API_MODE: "${FRONTEND_AUDIT_API_MODE:-mock}"
}
EOF

exec "$@"
