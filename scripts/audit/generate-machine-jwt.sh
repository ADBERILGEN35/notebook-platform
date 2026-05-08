#!/usr/bin/env bash
set -euo pipefail

PRIVATE_KEY_PATH="${MACHINE_JWT_PRIVATE_KEY_PATH:-}"
ISSUER="${MACHINE_JWT_ISSUER:-https://audit-exporter.internal}"
SUBJECT="${MACHINE_JWT_SUBJECT:-audit-exporter}"
AUDIENCE="${MACHINE_JWT_AUDIENCE:-api-gateway}"
SCOPE="${MACHINE_JWT_SCOPE:-admin:audit:export}"
TTL_SECONDS="${MACHINE_JWT_TTL_SECONDS:-600}"
KID="${MACHINE_JWT_KID:-}"
SERVICE_NAME="${MACHINE_JWT_SERVICE_NAME:-audit-exporter}"

if [[ -z "$PRIVATE_KEY_PATH" || ! -f "$PRIVATE_KEY_PATH" ]]; then
  echo "MACHINE_JWT_PRIVATE_KEY_PATH must point to an existing PEM private key." >&2
  exit 1
fi
if [[ "$TTL_SECONDS" -le 0 ]]; then
  echo "MACHINE_JWT_TTL_SECONDS must be > 0." >&2
  exit 1
fi

base64url() {
  openssl base64 -A | tr '+/' '-_' | tr -d '='
}

now="$(date -u +%s)"
exp="$((now + TTL_SECONDS))"
jti="$(openssl rand -hex 16)"

if [[ -n "$KID" ]]; then
  header="{\"alg\":\"RS256\",\"typ\":\"JWT\",\"kid\":\"${KID}\"}"
else
  header='{"alg":"RS256","typ":"JWT"}'
fi

payload="$(cat <<EOF
{"iss":"${ISSUER}","sub":"${SUBJECT}","aud":"${AUDIENCE}","scope":"${SCOPE}","token_type":"machine","service_name":"${SERVICE_NAME}","iat":${now},"exp":${exp},"jti":"${jti}"}
EOF
)"

header_b64="$(printf '%s' "$header" | base64url)"
payload_b64="$(printf '%s' "$payload" | base64url)"
signing_input="${header_b64}.${payload_b64}"
signature_b64="$(printf '%s' "$signing_input" | openssl dgst -binary -sha256 -sign "$PRIVATE_KEY_PATH" | base64url)"

printf '%s.%s\n' "$signing_input" "$signature_b64"
