#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
EMAIL="${SMOKE_EMAIL:-auth-security-$(date +%s)@example.com}"
PASSWORD="${SMOKE_PASSWORD:-StrongerPass123!}"

json_field() {
  python3 -c 'import json,sys; print(json.load(sys.stdin)[sys.argv[1]])' "$1"
}

request() {
  local method="$1"
  local path="$2"
  local body="${3:-}"
  local auth="${4:-}"
  local args=(-sS -X "$method" "$BASE_URL$path" -H "Content-Type: application/json")
  if [[ -n "$auth" ]]; then
    args+=(-H "Authorization: Bearer $auth")
  fi
  if [[ -n "$body" ]]; then
    args+=(-d "$body")
  fi
  curl "${args[@]}"
}

expect_status() {
  local expected="$1"
  local method="$2"
  local path="$3"
  local body="${4:-}"
  local auth="${5:-}"
  local status
  local args=(-sS -o /tmp/notebook-auth-security-response.json -w "%{http_code}" -X "$method" "$BASE_URL$path" -H "Content-Type: application/json")
  if [[ -n "$auth" ]]; then
    args+=(-H "Authorization: Bearer $auth")
  fi
  if [[ -n "$body" ]]; then
    args+=(-d "$body")
  fi
  status="$(curl "${args[@]}")"
  if [[ "$status" != "$expected" ]]; then
    echo "Expected $method $path to return $expected but got $status" >&2
    cat /tmp/notebook-auth-security-response.json >&2
    exit 1
  fi
}

echo "Checking gateway health..."
curl -sS -f "$BASE_URL/actuator/health" >/dev/null

echo "Signing up auth security smoke user $EMAIL..."
signup_response="$(request POST /auth/signup "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\",\"name\":\"Auth Security Smoke\"}")"
access_token="$(printf '%s' "$signup_response" | json_field accessToken)"
refresh_token="$(printf '%s' "$signup_response" | json_field refreshToken)"

echo "Revoking current refresh token with logout..."
expect_status 204 POST /auth/logout "{\"refreshToken\":\"$refresh_token\"}" "$access_token"

echo "Verifying logged out refresh token is rejected..."
expect_status 401 POST /auth/refresh "{\"refreshToken\":\"$refresh_token\"}"

echo "Creating two sessions..."
login_one="$(request POST /auth/login "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")"
access_one="$(printf '%s' "$login_one" | json_field accessToken)"
refresh_one="$(printf '%s' "$login_one" | json_field refreshToken)"
login_two="$(request POST /auth/login "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")"
refresh_two="$(printf '%s' "$login_two" | json_field refreshToken)"

echo "Revoking all active refresh tokens..."
expect_status 200 POST /auth/revoke-all '{"reason":"ACCOUNT_SECURITY"}' "$access_one"

echo "Verifying revoked sessions are rejected..."
expect_status 401 POST /auth/refresh "{\"refreshToken\":\"$refresh_one\"}"
expect_status 401 POST /auth/refresh "{\"refreshToken\":\"$refresh_two\"}"

echo "Auth security smoke test passed."
