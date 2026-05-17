#!/usr/bin/env bash
#
# Fixture-based tests for workspace/search retention smoke scripts (Faz 107).
# No live gateway or admin token required.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
WORKSPACE_SMOKE="$ROOT/scripts/retention/workspace-retention-dry-run-smoke.sh"
SEARCH_SMOKE="$ROOT/scripts/retention/search-retention-dry-run-smoke.sh"
FIXTURE_DIR="$(mktemp -d)"
trap 'rm -rf "$FIXTURE_DIR"' EXIT

export ADMIN_ACCESS_TOKEN="fixture-test-token"

write_fixture() {
  local name="$1"
  shift
  printf '%s' "$1" >"$FIXTURE_DIR/$name.json"
}

run_workspace() {
  local fixture="$1"
  local expect="$2"
  local expected_exit="$3"
  set +e
  RETENTION_SMOKE_FIXTURE_FILE="$FIXTURE_DIR/$fixture.json" \
    EXPECT_WORKSPACE_RETENTION_READY="$expect" \
    bash "$WORKSPACE_SMOKE"
  local code=$?
  set -e
  if [[ "$code" -ne "$expected_exit" ]]; then
    echo "workspace $fixture expect=$expect: expected exit $expected_exit got $code" >&2
    exit 1
  fi
}

run_search() {
  local fixture="$1"
  local expect="$2"
  local expected_exit="$3"
  set +e
  RETENTION_SMOKE_FIXTURE_FILE="$FIXTURE_DIR/$fixture.json" \
    EXPECT_SEARCH_RETENTION_READY="$expect" \
    bash "$SEARCH_SMOKE"
  local code=$?
  set -e
  if [[ "$code" -ne "$expected_exit" ]]; then
    echo "search $fixture expect=$expect: expected exit $expected_exit got $code" >&2
    exit 1
  fi
}

READY_BODY='{
  "targets": [
    {"targetKey":"workspace.invitations_expired","status":"DRY_RUN_READY","eligibleCount":5,"purgeableCount":5,"blockedByLegalHold":false,"warnings":[]},
    {"targetKey":"workspace.audit_like_events","status":"DRY_RUN_READY","eligibleCount":200,"purgeableCount":200,"blockedByLegalHold":false,"warnings":[]},
    {"targetKey":"search.documents_stale","status":"DRY_RUN_READY","eligibleCount":88,"purgeableCount":88,"blockedByLegalHold":false,"warnings":[]},
    {"targetKey":"search.reindex_jobs_terminal","status":"DRY_RUN_READY","eligibleCount":12,"purgeableCount":12,"blockedByLegalHold":false,"warnings":[]}
  ],
  "warnings": ["PLATFORM_RETENTION_WORKSPACE_PLAN_INCLUDED","PLATFORM_RETENTION_SEARCH_PLAN_INCLUDED"],
  "serviceSummaries": [
    {"service":"workspace-service","status":"READY","totalTargets":2},
    {"service":"search-service","status":"READY","totalTargets":2}
  ]
}'

DISABLED_BODY='{
  "targets": [],
  "warnings": ["WORKSPACE_RETENTION_DRY_RUN_DISABLED","SEARCH_RETENTION_DRY_RUN_DISABLED"],
  "serviceSummaries": []
}'

UNAVAILABLE_BODY='{
  "targets": [],
  "warnings": ["WORKSPACE_RETENTION_SERVICE_UNAVAILABLE","SEARCH_RETENTION_SERVICE_UNAVAILABLE"],
  "serviceSummaries": []
}'

PRIVACY_WORKSPACE_BODY='{
  "targets": [
    {"targetKey":"workspace.invitations_expired","status":"DRY_RUN_READY","eligibleCount":1,"purgeableCount":1,"blockedByLegalHold":false,"warnings":[]}
  ],
  "warnings": [],
  "serviceSummaries": [],
  "debug": {"userEmail":"leak@corp.example"}
}'

PRIVACY_SEARCH_BODY='{
  "targets": [
    {"targetKey":"search.documents_stale","status":"DRY_RUN_READY","eligibleCount":1,"purgeableCount":1,"blockedByLegalHold":false,"warnings":[]}
  ],
  "warnings": [],
  "serviceSummaries": [],
  "debug": {"snippet":"leaked snippet text"}
}'

BAD_SHAPE_BODY='{"foo": 1}'

write_fixture ready "$READY_BODY"
write_fixture disabled "$DISABLED_BODY"
write_fixture unavailable "$UNAVAILABLE_BODY"
write_fixture privacy_workspace "$PRIVACY_WORKSPACE_BODY"
write_fixture privacy_search "$PRIVACY_SEARCH_BODY"
write_fixture badshape "$BAD_SHAPE_BODY"

run_workspace ready true 0
run_workspace disabled false 0
run_workspace disabled true 2
run_workspace unavailable false 0
run_workspace unavailable true 2
run_workspace privacy_workspace false 3
run_workspace badshape false 4

run_search ready true 0
run_search disabled false 0
run_search disabled true 2
run_search unavailable false 0
run_search unavailable true 2
run_search privacy_search false 3
run_search badshape false 4

echo "All workspace/search retention smoke fixture scenarios passed."
