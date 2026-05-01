#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
IMAGE_PREFIX="${IMAGE_PREFIX:-notebook-platform}"
IMAGE_TAG="${IMAGE_TAG:-phase17-local}"
BUILD_IMAGES="${BUILD_IMAGES:-true}"
ALLOW_SECURITY_TOOL_SKIP="${ALLOW_SECURITY_TOOL_SKIP:-false}"
HIGH_EXIT_CODE="${HIGH_EXIT_CODE:-0}"
SERVICES=(api-gateway identity-service workspace-service content-service)

if ! command -v trivy >/dev/null 2>&1; then
  if [[ "$ALLOW_SECURITY_TOOL_SKIP" == "true" ]]; then
    echo "trivy is not installed; skipping image vulnerability scan." >&2
    exit 0
  fi
  echo "trivy is required. Install Trivy or set ALLOW_SECURITY_TOOL_SKIP=true for local-only skips." >&2
  exit 1
fi

if [[ "$BUILD_IMAGES" == "true" ]] && ! command -v docker >/dev/null 2>&1; then
  if [[ "$ALLOW_SECURITY_TOOL_SKIP" == "true" ]]; then
    echo "docker is not installed; skipping image build and scan." >&2
    exit 0
  fi
  echo "docker is required when BUILD_IMAGES=true." >&2
  exit 1
fi

for service in "${SERVICES[@]}"; do
  image="$IMAGE_PREFIX/$service:$IMAGE_TAG"
  if [[ "$BUILD_IMAGES" == "true" ]]; then
    docker build -f "$ROOT_DIR/$service/Dockerfile" -t "$image" "$ROOT_DIR"
  fi

  echo "Scanning $image for CRITICAL vulnerabilities"
  trivy image --ignore-unfixed --severity CRITICAL --exit-code 1 "$image"

  echo "Scanning $image for HIGH vulnerabilities; HIGH_EXIT_CODE=$HIGH_EXIT_CODE"
  trivy image --ignore-unfixed --severity HIGH --exit-code "$HIGH_EXIT_CODE" "$image"
done
