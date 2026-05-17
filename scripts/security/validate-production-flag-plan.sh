#!/usr/bin/env bash
#
# Validate Helm/GitOps values against production flag flip plan guardrails (Faz 126).
# Does NOT enable flags; asserts values files stay in safe default state.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CHART_VALUES="$ROOT/deploy/helm/notebook-platform/values.yaml"
PROD_VALUES="$ROOT/deploy/gitops/environments/prod/values.yaml"
CUSTOM_FILE=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --values-file) CUSTOM_FILE="$2"; shift 2 ;;
    *) echo "unknown arg: $1" >&2; exit 2 ;;
  esac
done

expect_kv_false() {
  local file="$1"
  local key="$2"
  if grep -qE "^[[:space:]]*${key}:[[:space:]]*\"?true\"?[[:space:]]*$" "$file"; then
    echo "  FAIL: ${key} must be false" >&2
    return 1
  fi
}

scan_file() {
  local file="$1"
  local label="$2"
  local failed=0
  [[ -f "$file" ]] || { echo "missing: $file" >&2; return 1; }
  echo "==> $label ($file)"

  local keys=(
    breakGlassEnabled breakGlassRevocationEnabled gatewayBreakGlassDenylistCheckEnabled
    gatewayBreakGlassAdminAllowed breakGlassAllowAdminWrite
    scimDeltaProviderPocEnabled scimDeltaRemoteFetchEnabled scimDeltaRemoteMultiPageEnabled
    scimDeltaSyncEnabled platformRetentionGovernanceEnabled
    contentRetentionDryRunEnabled contentRetentionIntegrationEnabled
    notificationRetentionDryRunCountsEnabled notificationRetentionIntegrationEnabled
    workspaceRetentionDryRunCountsEnabled workspaceRetentionIntegrationEnabled
    searchRetentionDryRunCountsEnabled searchRetentionIntegrationEnabled
    adminGitopsPrEnabled notificationRetentionWorkerEnabled
  )
  for k in "${keys[@]}"; do
    expect_kv_false "$file" "$k" || failed=1
  done

  if grep -qE '^[[:space:]]*adminGitopsPrEnabled:[[:space:]]*"?true"?[[:space:]]*$' "$file" \
    && grep -qE '^[[:space:]]*adminGitopsProvider:[[:space:]]*"?mock"?[[:space:]]*$' "$file"; then
    echo "  FAIL: adminGitopsPrEnabled=true with adminGitopsProvider=mock" >&2
    failed=1
  fi

  if grep -qE '^[[:space:]]*scimDeltaRemoteFetchEnabled:[[:space:]]*"?true"?[[:space:]]*$' "$file"; then
    if ! grep -qE '^[[:space:]]*scimDeltaDryRunOnly:[[:space:]]*"?true"?[[:space:]]*$' "$file"; then
      echo "  FAIL: scimDeltaRemoteFetchEnabled requires scimDeltaDryRunOnly=true" >&2
      failed=1
    fi
  fi

  if grep -q 'FRONTEND_NOTIFICATION_RETENTION_PURGE_ENABLED: "true"' "$file"; then
    echo "  FAIL: FRONTEND_NOTIFICATION_RETENTION_PURGE_ENABLED must be false" >&2
    failed=1
  fi

  if grep -qE '^[[:space:]]*notificationRetentionWorkerEnabled:[[:space:]]*"?true"?[[:space:]]*$' "$file"; then
    if ! grep -qE '^[[:space:]]*notificationRetentionDryRunOnly:[[:space:]]*"?true"?[[:space:]]*$' "$file"; then
      echo "  FAIL: notificationRetentionWorkerEnabled requires notificationRetentionDryRunOnly=true" >&2
      failed=1
    fi
  fi

  if grep -qE '^retentionDatasource:' "$file"; then
    for svc in content notification workspace search; do
      if awk -v svc="$svc" '
        /^retentionDatasource:/ { inroot=1 }
        inroot && $0 ~ "^  " svc ":$" { inblock=1 }
        inblock && /^  [a-z]/ && $0 !~ "^  " svc ":" { exit }
        inblock && /enabled:/ { print; exit }
      ' "$file" | grep -q 'enabled: true'; then
        echo "  FAIL: retentionDatasource.${svc}.enabled must be false" >&2
        failed=1
      fi
    done
  fi

  if [[ "$failed" -ne 0 ]]; then
    echo "FAIL $label" >&2
    return 1
  fi
  echo "PASS $label"
}

if [[ -n "$CUSTOM_FILE" ]]; then
  scan_file "$CUSTOM_FILE" "custom values"
  exit 0
fi

scan_file "$CHART_VALUES" "chart defaults"
scan_file "$PROD_VALUES" "gitops prod overlay"
echo "production flag plan validation passed."
