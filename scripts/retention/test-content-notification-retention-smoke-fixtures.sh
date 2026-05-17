#!/usr/bin/env bash
#
# Fixture-based tests for content/notification retention smoke scripts (Faz 108).
# No live gateway or admin token required.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CONTENT_SMOKE="$ROOT/scripts/retention/content-retention-dry-run-smoke.sh"
NOTIFICATION_SMOKE="$ROOT/scripts/retention/notification-retention-dry-run-smoke.sh"
FIXTURE_DIR="$(mktemp -d)"
trap 'rm -rf "$FIXTURE_DIR"' EXIT

export ADMIN_ACCESS_TOKEN="fixture-test-token"

write_fixture() {
  local name="$1"
  shift
  printf '%s' "$1" >"$FIXTURE_DIR/$name.json"
}

run_content() {
  local fixture="$1"
  local expect="$2"
  local expected_exit="$3"
  set +e
  RETENTION_SMOKE_FIXTURE_FILE="$FIXTURE_DIR/$fixture.json" \
    EXPECT_CONTENT_RETENTION_READY="$expect" \
    bash "$CONTENT_SMOKE"
  local code=$?
  set -e
  if [[ "$code" -ne "$expected_exit" ]]; then
    echo "content $fixture expect=$expect: expected exit $expected_exit got $code" >&2
    exit 1
  fi
}

run_notification() {
  local fixture="$1"
  local expect="$2"
  local expected_exit="$3"
  set +e
  RETENTION_SMOKE_FIXTURE_FILE="$FIXTURE_DIR/$fixture.json" \
    EXPECT_NOTIFICATION_RETENTION_READY="$expect" \
    bash "$NOTIFICATION_SMOKE"
  local code=$?
  set -e
  if [[ "$code" -ne "$expected_exit" ]]; then
    echo "notification $fixture expect=$expect: expected exit $expected_exit got $code" >&2
    exit 1
  fi
}

CONTENT_READY_BODY='{
  "targets": [
    {"targetKey":"content.note_versions","status":"DRY_RUN_READY","eligibleCount":10,"purgeableCount":10,"blockedByLegalHold":false,"warnings":[]},
    {"targetKey":"content.comments","status":"DRY_RUN_READY","eligibleCount":5,"purgeableCount":5,"blockedByLegalHold":false,"warnings":[]},
    {"targetKey":"content.search_documents","status":"DRY_RUN_READY","eligibleCount":3,"purgeableCount":3,"blockedByLegalHold":false,"warnings":[]}
  ],
  "warnings": ["PLATFORM_RETENTION_CONTENT_PLAN_INCLUDED"],
  "serviceSummaries": [{"service":"content-service","status":"READY","totalTargets":3}]
}'

CONTENT_UNAVAILABLE_BODY='{
  "targets": [],
  "warnings": ["CONTENT_RETENTION_SERVICE_UNAVAILABLE"],
  "serviceSummaries": []
}'

CONTENT_PRIVACY_BODY='{
  "targets": [
    {"targetKey":"content.note_versions","status":"DRY_RUN_READY","eligibleCount":1,"purgeableCount":1,"blockedByLegalHold":false,"warnings":[]}
  ],
  "warnings": [],
  "serviceSummaries": [],
  "debug": {"noteBody":"leaked body text"}
}'

NOTIF_READY_BODY='{
  "targets": [
    {"targetKey":"notification.analytics_hourly","status":"DRY_RUN_READY","eligibleCount":1,"purgeableCount":1,"blockedByLegalHold":false,"warnings":[]},
    {"targetKey":"notification.fanout_outbox_sent","status":"DRY_RUN_READY","eligibleCount":2,"purgeableCount":2,"blockedByLegalHold":false,"warnings":[]},
    {"targetKey":"notification.fanout_outbox_dead","status":"DRY_RUN_READY","eligibleCount":0,"purgeableCount":0,"blockedByLegalHold":false,"warnings":[]},
    {"targetKey":"notification.dead_letter_requeue_requests","status":"DRY_RUN_READY","eligibleCount":0,"purgeableCount":0,"blockedByLegalHold":false,"warnings":[]},
    {"targetKey":"notification.digest_items_terminal","status":"DRY_RUN_READY","eligibleCount":4,"purgeableCount":4,"blockedByLegalHold":false,"warnings":[]},
    {"targetKey":"notification.email_notifications_terminal","status":"DRY_RUN_READY","eligibleCount":9,"purgeableCount":9,"blockedByLegalHold":false,"warnings":[]}
  ],
  "warnings": ["PLATFORM_RETENTION_NOTIFICATION_PLAN_INCLUDED"],
  "serviceSummaries": [{"service":"notification-service","status":"READY","totalTargets":6}]
}'

NOTIF_UNAVAILABLE_BODY='{
  "targets": [],
  "warnings": ["NOTIFICATION_RETENTION_SERVICE_UNAVAILABLE"],
  "serviceSummaries": []
}'

NOTIF_PRIVACY_BODY='{
  "targets": [
    {"targetKey":"notification.analytics_hourly","status":"DRY_RUN_READY","eligibleCount":1,"purgeableCount":1,"blockedByLegalHold":false,"warnings":[]}
  ],
  "warnings": [],
  "serviceSummaries": [],
  "debug": {"recipientEmail":"leak@corp.example"}
}'

BAD_SHAPE_BODY='{"foo": 1}'

write_fixture content_ready "$CONTENT_READY_BODY"
write_fixture content_unavailable "$CONTENT_UNAVAILABLE_BODY"
write_fixture content_privacy "$CONTENT_PRIVACY_BODY"
write_fixture notif_ready "$NOTIF_READY_BODY"
write_fixture notif_unavailable "$NOTIF_UNAVAILABLE_BODY"
write_fixture notif_privacy "$NOTIF_PRIVACY_BODY"
write_fixture badshape "$BAD_SHAPE_BODY"

run_content content_ready true 0
run_content content_unavailable false 0
run_content content_unavailable true 2
run_content content_privacy false 3
run_content badshape false 4

run_notification notif_ready true 0
run_notification notif_unavailable false 0
run_notification notif_unavailable true 2
run_notification notif_privacy false 3
run_notification badshape false 4

echo "All content/notification retention smoke fixture scenarios passed."
