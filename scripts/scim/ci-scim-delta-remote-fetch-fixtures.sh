#!/usr/bin/env bash
#
# CI: SCIM delta remote fetch wiring + evidence scripts (Faz 118). No live IdP or secrets required.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"
CHART="$ROOT/deploy/helm/notebook-platform"
RENDER_DIR="$(mktemp -d)"
trap 'rm -rf "$RENDER_DIR"' EXIT

echo "==> bash -n scripts/scim/*.sh"
for script in scripts/scim/*.sh; do
  bash -n "$script"
  echo "  syntax ok: $script"
done

echo "==> not-configured smoke fixture"
OUT="$(mktemp -d)"
SCIM_DELTA_EVIDENCE_OUTPUT_DIR="$OUT" bash scripts/scim/scim-delta-remote-fetch-smoke.sh
python3 -m json.tool "$OUT/scim-delta-remote-fetch-evidence.json" >/dev/null
grep -q '"evidenceStatus": "skipped"' "$OUT/scim-delta-remote-fetch-evidence.json"
FORBIDDEN='Bearer |Authorization|userName|password'
if grep -qE "$FORBIDDEN" "$OUT/scim-delta-remote-fetch-evidence.json"; then
  echo "forbidden pattern in fixture evidence" >&2
  exit 1
fi

echo "==> evidence template generator"
TEMPLATE_TMP="$(mktemp)"
bash scripts/scim/generate-scim-delta-evidence-template.sh >"$TEMPLATE_TMP"
if grep -qE 'Bearer [A-Za-z0-9_-]{24,}|password:[[:space:]]*[A-Za-z0-9+/=]{16,}' "$TEMPLATE_TMP"; then
  echo "forbidden pattern in template" >&2
  exit 1
fi
grep -q 'CR-PLACEHOLDER' "$TEMPLATE_TMP"
rm -f "$TEMPLATE_TMP"

if command -v helm >/dev/null 2>&1; then
  echo "==> helm render default (remote fetch disabled)"
  helm template notebook-platform "$CHART" >"$RENDER_DIR/default.yaml"

  echo "==> helm render okta overlay example"
  helm template notebook-platform "$CHART" \
    -f "$CHART/examples/scim-delta-remote-fetch/okta.overlay.example.yaml" \
    >"$RENDER_DIR/okta.yaml"

  if ! grep -q 'name: SCIM_DELTA_REMOTE_BEARER_TOKEN' "$RENDER_DIR/okta.yaml"; then
    echo "expected SCIM_DELTA_REMOTE_BEARER_TOKEN env in okta overlay render" >&2
    exit 1
  fi
  if ! grep -A6 'name: SCIM_DELTA_REMOTE_BEARER_TOKEN' "$RENDER_DIR/okta.yaml" | grep -q 'secretKeyRef'; then
    echo "expected secretKeyRef for SCIM_DELTA_REMOTE_BEARER_TOKEN" >&2
    exit 1
  fi
  if grep -E 'SCIM_DELTA_REMOTE_BEARER_TOKEN.*value:' "$RENDER_DIR/okta.yaml"; then
    echo "bearer token must not be a literal env value" >&2
    exit 1
  fi

  if grep -q 'SCIM_DELTA_REMOTE_FETCH_ENABLED: "true"' "$RENDER_DIR/default.yaml"; then
    echo "default chart must keep remote fetch disabled" >&2
    exit 1
  fi
else
  echo "helm not installed; skipping template assertions"
fi

echo "==> grep deploy for hardcoded bearer literals in values (excluding placeholders)"
if grep -RIn --include='values.yaml' --include='*.example.yaml' \
  -E 'scimDeltaRemote.*:[[:space:]]*["'\'']?[A-Za-z0-9+/=]{24,}["'\'']?' \
  deploy/helm deploy/gitops 2>/dev/null | grep -v '<' | grep -v 'PLACEHOLDER'; then
  echo "possible scim delta token literal in values" >&2
  exit 1
fi

echo "==> check-no-secrets"
bash scripts/check-no-secrets.sh

echo "SCIM delta remote fetch CI fixtures passed."
