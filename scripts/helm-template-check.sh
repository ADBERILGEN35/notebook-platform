#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CHART_DIR="$ROOT_DIR/deploy/helm/notebook-platform"

if ! command -v helm >/dev/null 2>&1; then
  echo "helm is not installed; skipping helm lint/template validation." >&2
  exit 0
fi

helm lint "$CHART_DIR"
helm template notebook-platform "$CHART_DIR" >/tmp/notebook-platform-default.yaml
helm template notebook-platform "$CHART_DIR" -f "$CHART_DIR/values-dev.yaml" >/tmp/notebook-platform-dev.yaml
helm template notebook-platform "$CHART_DIR" -f "$CHART_DIR/values-prod.example.yaml" >/tmp/notebook-platform-prod.yaml
helm template notebook-platform "$CHART_DIR" \
  --set secrets.mode=external-secrets \
  --set secrets.externalSecrets.enabled=true \
  --set secrets.externalSecrets.secretStoreRef.name=notebook-platform-secret-store \
  >/tmp/notebook-platform-external-secrets.yaml
helm template notebook-platform "$CHART_DIR" \
  --set secrets.mode=native-kubernetes-secret \
  --set secrets.create=false \
  --set secrets.existingSecret=precreated-notebook-platform-secrets \
  >/tmp/notebook-platform-existing-secret.yaml
helm template notebook-platform "$CHART_DIR" \
  --set autoscaling.enabled=true \
  >/tmp/notebook-platform-hpa.yaml

echo "Helm chart rendered successfully:"
echo "  /tmp/notebook-platform-default.yaml"
echo "  /tmp/notebook-platform-dev.yaml"
echo "  /tmp/notebook-platform-prod.yaml"
echo "  /tmp/notebook-platform-external-secrets.yaml"
echo "  /tmp/notebook-platform-existing-secret.yaml"
echo "  /tmp/notebook-platform-hpa.yaml"
