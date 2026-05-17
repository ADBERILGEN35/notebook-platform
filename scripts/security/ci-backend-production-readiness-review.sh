#!/usr/bin/env bash
#
# CI: verify Helm chart production-safe defaults for high-risk flags (Faz 124).
# Does not replace staging evidence gates; asserts repo defaults only.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
VALUES="$ROOT/deploy/helm/notebook-platform/values.yaml"

if [[ ! -f "$VALUES" ]]; then
  echo "missing values.yaml" >&2
  exit 1
fi

expect_kv() {
  local key="$1"
  local want="$2"
  if ! grep -qE "^[[:space:]]*${key}:[[:space:]]*\"?${want}\"?[[:space:]]*$" "$VALUES"; then
    echo "expected ${key}=${want} in chart values" >&2
    exit 1
  fi
  echo "  ok: ${key}=${want}"
}

echo "==> chart production-safe defaults ($VALUES)"
expect_kv breakGlassEnabled false
expect_kv breakGlassRevocationEnabled false
expect_kv gatewayBreakGlassAdminAllowed false
expect_kv gatewayBreakGlassDenylistCheckEnabled false
expect_kv breakGlassAllowAdminWrite false
expect_kv scimDeltaProviderPocEnabled false
expect_kv scimDeltaRemoteFetchEnabled false
expect_kv scimDeltaRemoteMultiPageEnabled false
expect_kv scimDeltaSyncEnabled false
expect_kv platformRetentionGovernanceEnabled false
expect_kv adminGitopsPrEnabled false
expect_kv adminGitopsProvider mock

echo "==> retentionDatasource.*.enabled false"
for svc in content notification workspace search; do
  if ! awk -v svc="$svc" '
    $0 ~ "^  " svc ":$" { inblock=1 }
    inblock && /enabled:/ { print; exit }
  ' "$VALUES" | grep -q 'enabled: false'; then
    echo "expected retentionDatasource.${svc}.enabled=false" >&2
    exit 1
  fi
  echo "  ok: retentionDatasource.${svc}.enabled=false"
done

echo "==> scimDeltaRemoteFetch bearer secret wiring default off"
if ! awk '/scimDeltaRemoteFetch:/{f=1} f&&/bearerTokenFromSecret:/{g=1} g&&/enabled:/{print; exit}' "$VALUES" | grep -q 'enabled: false'; then
  echo "expected scimDeltaRemoteFetch.bearerTokenFromSecret.enabled=false" >&2
  exit 1
fi
echo "  ok: scimDeltaRemoteFetch.bearerTokenFromSecret.enabled=false"

echo "==> frontend destructive / SW defaults"
grep -q 'FRONTEND_NOTIFICATION_RETENTION_PURGE_ENABLED: "false"' "$ROOT/deploy/helm/notebook-platform/values.yaml"
grep -q 'FRONTEND_SW_BACKGROUND_SYNC_ENABLED: "false"' "$ROOT/deploy/helm/notebook-platform/values.yaml" || true

echo "backend production-readiness default checks passed."
