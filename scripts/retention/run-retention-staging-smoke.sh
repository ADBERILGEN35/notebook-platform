#!/usr/bin/env bash
#
# Staging retention smoke orchestrator (Faz 108 / Faz 109 evidence).
# Runs content, notification, workspace, and search dry-run smokes against a gateway.
#
# Required env (no defaults for secrets):
#   API_BASE_URL
#   ADMIN_ACCESS_TOKEN          never logged
#
# Optional:
#   RETENTION_STAGING_SMOKE_OUTPUT_DIR  (default: ./retention-staging-smoke-out)
#   EXPECT_*_RETENTION_READY
#
# Writes sanitized artifacts:
#   retention-staging-smoke-results.json
#   retention-staging-smoke-summary.md
#
# When API_BASE_URL or ADMIN_ACCESS_TOKEN is empty, exits 0 (graceful skip).
#
# Aggregated exit (privacy highest priority):
#   0 ok / skip
#   2 readiness gap
#   3 privacy violation (wins over other failures)
#   4 shape mismatch

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

OUTPUT_DIR="${RETENTION_STAGING_SMOKE_OUTPUT_DIR:-$ROOT/retention-staging-smoke-out}"
mkdir -p "$OUTPUT_DIR"
NDJSON="$OUTPUT_DIR/domains.ndjson"
: >"$NDJSON"

write_skip_artifacts() {
  python3 "$ROOT/scripts/retention/write-retention-staging-report.py" \
    --output-dir "$OUTPUT_DIR" \
    --skip "skipped: missing staging secrets" \
    --github-summary
}

if [[ -z "${API_BASE_URL:-}" || -z "${ADMIN_ACCESS_TOKEN:-}" ]]; then
  echo "Skipping staging retention smoke: API_BASE_URL or ADMIN_ACCESS_TOKEN not configured."
  write_skip_artifacts
  exit 0
fi

export API_BASE_URL
export ADMIN_ACCESS_TOKEN
unset ACCESS_TOKEN BASE_URL 2>/dev/null || true

declare -a NAMES=(
  content
  notification
  workspace
  search
)
declare -a SCRIPTS=(
  scripts/retention/content-retention-dry-run-smoke.sh
  scripts/retention/notification-retention-dry-run-smoke.sh
  scripts/retention/workspace-retention-dry-run-smoke.sh
  scripts/retention/search-retention-dry-run-smoke.sh
)
declare -a EXPECT_VARS=(
  EXPECT_CONTENT_RETENTION_READY
  EXPECT_NOTIFICATION_RETENTION_READY
  EXPECT_WORKSPACE_RETENTION_READY
  EXPECT_SEARCH_RETENTION_READY
)

final=0
update_final() {
  local code="$1"
  if [[ "$code" -eq 3 ]]; then
    final=3
  elif [[ "$code" -eq 4 && "$final" -ne 3 ]]; then
    final=4
  elif [[ "$code" -eq 2 && "$final" -ne 3 && "$final" -ne 4 ]]; then
    final=2
  elif [[ "$code" -ne 0 && "$final" -eq 0 ]]; then
    final="$code"
  fi
}

append_ndjson() {
  local domain="$1"
  local exit_code="$2"
  local expect_ready="$3"
  local output_file="$4"
  python3 - "$NDJSON" "$domain" "$exit_code" "$expect_ready" "$output_file" <<'PY'
import json
import sys

path, domain, exit_code, expect_ready, output_file = sys.argv[1:6]
output = open(output_file, encoding="utf-8").read()
row = {
    "domain": domain,
    "exitCode": int(exit_code),
    "expectReady": expect_ready.lower() == "true",
    "output": output,
}
with open(path, "a", encoding="utf-8") as fp:
    fp.write(json.dumps(row) + "\n")
PY
}

sanitize_log() {
  grep -E '^(Calling |Using fixture|targets seen|Warnings:|serviceSummaries|Readiness|Privacy|Unexpected HTTP|production-ready|not enabled|Content |Notification |Workspace |Search )' || true
}

for i in "${!NAMES[@]}"; do
  name="${NAMES[$i]}"
  script="${SCRIPTS[$i]}"
  expect_var="${EXPECT_VARS[$i]}"
  expect_val="${!expect_var:-false}"

  echo "==> ${name} retention smoke (expect ready: ${expect_val})"
  domain_out="$(mktemp)"
  set +e
  API_BASE_URL="$API_BASE_URL" \
    ADMIN_ACCESS_TOKEN="$ADMIN_ACCESS_TOKEN" \
    "${expect_var}=${expect_val}" \
    bash "$ROOT/$script" >"$domain_out" 2>&1
  code=$?
  set -e

  sanitize_log <"$domain_out"
  append_ndjson "$name" "$code" "$expect_val" "$domain_out"
  rm -f "$domain_out"

  if [[ "$code" -eq 0 ]]; then
    echo "${name} smoke: completed (exit 0)"
  else
    echo "${name} smoke: failed (exit ${code})"
    update_final "$code"
  fi
done

python3 "$ROOT/scripts/retention/write-retention-staging-report.py" \
  --output-dir "$OUTPUT_DIR" \
  --ndjson "$NDJSON" \
  --aggregated-exit "$final" \
  --github-summary

rm -f "$NDJSON"

if [[ "$final" -eq 0 ]]; then
  echo "Staging retention smoke summary: all passed or expected-gap"
else
  echo "Staging retention smoke summary: failed (aggregated exit ${final})"
fi
exit "$final"
