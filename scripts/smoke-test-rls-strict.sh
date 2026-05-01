#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
EMAIL="${SMOKE_EMAIL:-rls-smoke-$(date +%s)@example.com}"
PASSWORD="${SMOKE_PASSWORD:-StrongerPass123!}"
STRICT_MODE_EXPECTED="${STRICT_MODE_EXPECTED:-true}"
ACCESS_TOKEN="${ACCESS_TOKEN:-}"

json_field() {
  python3 -c 'import json,sys; print(json.load(sys.stdin)[sys.argv[1]])' "$1"
}

request() {
  local method="$1"
  local path="$2"
  local body="${3:-}"
  local auth="${4:-}"
  local extra_header="${5:-}"
  local args=(-sS -f -X "$method" "$BASE_URL$path" -H "Content-Type: application/json")
  if [[ -n "$auth" ]]; then
    args+=(-H "Authorization: Bearer $auth")
  fi
  if [[ -n "$extra_header" ]]; then
    args+=(-H "$extra_header")
  fi
  if [[ -n "$body" ]]; then
    args+=(-d "$body")
  fi
  curl "${args[@]}"
}

status_request() {
  local method="$1"
  local path="$2"
  local body="${3:-}"
  local auth="${4:-}"
  local extra_header="${5:-}"
  local args=(-sS -o /tmp/notebook-rls-smoke-response.json -w "%{http_code}" -X "$method" "$BASE_URL$path" -H "Content-Type: application/json")
  if [[ -n "$auth" ]]; then
    args+=(-H "Authorization: Bearer $auth")
  fi
  if [[ -n "$extra_header" ]]; then
    args+=(-H "$extra_header")
  fi
  if [[ -n "$body" ]]; then
    args+=(-d "$body")
  fi
  curl "${args[@]}"
}

expect_status() {
  local expected="$1"
  local actual="$2"
  local label="$3"
  if [[ "$actual" != "$expected" ]]; then
    echo "$label expected HTTP $expected but got $actual" >&2
    cat /tmp/notebook-rls-smoke-response.json >&2 || true
    exit 1
  fi
}

echo "Checking gateway health..."
curl -sS -f "$BASE_URL/actuator/health" >/dev/null

if [[ -z "$ACCESS_TOKEN" ]]; then
  echo "Signing up RLS smoke user $EMAIL..."
  signup_response="$(request POST /auth/signup "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\",\"name\":\"RLS Smoke User\"}")"
  ACCESS_TOKEN="$(printf '%s' "$signup_response" | json_field accessToken)"
fi

echo "Creating workspace..."
workspace_response="$(request POST /workspaces '{"name":"RLS Smoke Workspace","type":"TEAM"}' "$ACCESS_TOKEN")"
workspace_id="$(printf '%s' "$workspace_response" | json_field id)"

echo "Creating notebook..."
notebook_response="$(request POST "/workspaces/$workspace_id/notebooks" '{"name":"RLS Smoke Notebook","icon":"book"}' "$ACCESS_TOKEN")"
notebook_id="$(printf '%s' "$notebook_response" | json_field id)"

echo "Creating note..."
note_body='{"title":"RLS Smoke Note","contentBlocks":[{"id":"b1","type":"paragraph","content":[]}]}'
note_response="$(request POST "/notebooks/$notebook_id/notes" "$note_body" "$ACCESS_TOKEN" "X-Workspace-Id: $workspace_id")"
note_id="$(printf '%s' "$note_response" | json_field id)"

echo "Reading note with correct X-Workspace-Id..."
request GET "/notes/$note_id" "" "$ACCESS_TOKEN" "X-Workspace-Id: $workspace_id" >/dev/null

echo "Updating note with correct X-Workspace-Id..."
update_body='{"title":"RLS Smoke Note Updated","contentBlocks":[{"id":"b1","type":"paragraph","content":[]}]}'
request PATCH "/notes/$note_id" "$update_body" "$ACCESS_TOKEN" "X-Workspace-Id: $workspace_id" >/dev/null

echo "Creating comment with correct X-Workspace-Id..."
request POST "/notes/$note_id/comments" '{"blockId":"b1","content":"RLS smoke comment"}' "$ACCESS_TOKEN" "X-Workspace-Id: $workspace_id" >/dev/null

echo "Searching notes with workspaceId query..."
request GET "/notes/search?workspaceId=$workspace_id&q=RLS" "" "$ACCESS_TOKEN" "X-Workspace-Id: $workspace_id" >/dev/null

if [[ "$STRICT_MODE_EXPECTED" == "true" ]]; then
  echo "Checking strict mode missing X-Workspace-Id rejection..."
  missing_status="$(status_request GET "/notes/$note_id" "" "$ACCESS_TOKEN")"
  expect_status 400 "$missing_status" "missing X-Workspace-Id"

  echo "Checking strict mode conflicting X-Workspace-Id rejection..."
  wrong_workspace_id="00000000-0000-0000-0000-000000000001"
  conflict_status="$(status_request GET "/notes/$note_id" "" "$ACCESS_TOKEN" "X-Workspace-Id: $wrong_workspace_id")"
  expect_status 400 "$conflict_status" "conflicting X-Workspace-Id"
else
  echo "STRICT_MODE_EXPECTED=false; strict negative checks skipped."
fi

echo "RLS strict smoke test passed."
