#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
EMAIL="${SMOKE_EMAIL:-smoke-$(date +%s)@example.com}"
PASSWORD="${SMOKE_PASSWORD:-StrongerPass123!}"

json_field() {
  python3 -c 'import json,sys; print(json.load(sys.stdin)[sys.argv[1]])' "$1"
}

assert_page_items() {
  python3 -c 'import json,sys; data=json.load(sys.stdin); assert isinstance(data.get("items"), list), data; assert "page" in data and "size" in data and "hasNext" in data, data'
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

echo "Checking gateway health..."
curl -sS -f "$BASE_URL/actuator/health" >/dev/null

echo "Signing up smoke user $EMAIL..."
signup_response="$(request POST /auth/signup "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\",\"name\":\"Smoke User\"}")"
access_token="$(printf '%s' "$signup_response" | json_field accessToken)"

echo "Logging in..."
login_response="$(request POST /auth/login "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")"
access_token="$(printf '%s' "$login_response" | json_field accessToken)"

echo "Creating workspace..."
workspace_response="$(request POST /workspaces '{"name":"Smoke Workspace","type":"TEAM"}' "$access_token")"
workspace_id="$(printf '%s' "$workspace_response" | json_field id)"

echo "Checking paginated workspace list..."
request GET "/workspaces?page=0&size=20&sort=createdAt,desc" "" "$access_token" | assert_page_items

echo "Creating notebook..."
notebook_response="$(request POST "/workspaces/$workspace_id/notebooks" '{"name":"Smoke Notebook","icon":"book"}' "$access_token")"
notebook_id="$(printf '%s' "$notebook_response" | json_field id)"

echo "Checking paginated notebook list..."
request GET "/workspaces/$workspace_id/notebooks?page=0&size=20&sort=createdAt,desc" "" "$access_token" | assert_page_items

echo "Creating note..."
note_body='{"title":"Smoke Note","contentBlocks":[{"id":"b1","type":"paragraph","content":[]}]}'
note_response="$(request POST "/notebooks/$notebook_id/notes" "$note_body" "$access_token" "X-Workspace-Id: $workspace_id")"
note_id="$(printf '%s' "$note_response" | json_field id)"

echo "Checking paginated note list..."
request GET "/notebooks/$notebook_id/notes?page=0&size=20&sort=createdAt,desc" "" "$access_token" "X-Workspace-Id: $workspace_id" | assert_page_items

echo "Updating note..."
update_body='{"title":"Smoke Note Updated","contentBlocks":[{"id":"b1","type":"paragraph","content":[]}]}'
request PATCH "/notes/$note_id" "$update_body" "$access_token" "X-Workspace-Id: $workspace_id" >/dev/null

echo "Listing versions..."
request GET "/notes/$note_id/versions" "" "$access_token" "X-Workspace-Id: $workspace_id" >/dev/null

echo "Creating comment..."
request POST "/notes/$note_id/comments" '{"blockId":"b1","content":"Smoke comment"}' "$access_token" "X-Workspace-Id: $workspace_id" >/dev/null

echo "Checking paginated comment list..."
request GET "/notes/$note_id/comments?page=0&size=20&sort=createdAt,desc" "" "$access_token" "X-Workspace-Id: $workspace_id" | assert_page_items

echo "Searching notes..."
request GET "/notes/search?workspaceId=$workspace_id&q=Smoke&page=0&size=20&sort=updatedAt,desc" "" "$access_token" "X-Workspace-Id: $workspace_id" | assert_page_items

echo "Smoke test passed."
