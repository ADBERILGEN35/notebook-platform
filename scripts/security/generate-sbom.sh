#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
IMAGE_REGISTRY="${IMAGE_REGISTRY:-}"
IMAGE_REPOSITORY_PREFIX="${IMAGE_REPOSITORY_PREFIX:-${IMAGE_PREFIX:-notebook-platform}}"
IMAGE_TAG="${IMAGE_TAG:-phase17-local}"
SBOM_DIR="${SBOM_DIR:-$ROOT_DIR/sbom}"
SBOM_FORMAT="${SBOM_FORMAT:-spdx-json}"
BUILD_IMAGES="${BUILD_IMAGES:-true}"
ALLOW_SECURITY_TOOL_SKIP="${ALLOW_SECURITY_TOOL_SKIP:-false}"
SERVICES=(api-gateway identity-service workspace-service content-service)

if ! command -v syft >/dev/null 2>&1; then
  if [[ "$ALLOW_SECURITY_TOOL_SKIP" == "true" ]]; then
    echo "syft is not installed; skipping SBOM generation." >&2
    exit 0
  fi
  echo "syft is required. Install Syft or set ALLOW_SECURITY_TOOL_SKIP=true for local-only skips." >&2
  exit 1
fi

if [[ "$BUILD_IMAGES" == "true" ]] && ! command -v docker >/dev/null 2>&1; then
  if [[ "$ALLOW_SECURITY_TOOL_SKIP" == "true" ]]; then
    echo "docker is not installed; skipping image build and SBOM generation." >&2
    exit 0
  fi
  echo "docker is required when BUILD_IMAGES=true." >&2
  exit 1
fi

mkdir -p "$SBOM_DIR"

for service in "${SERVICES[@]}"; do
  if [[ -n "$IMAGE_REGISTRY" ]]; then
    image="$IMAGE_REGISTRY/$IMAGE_REPOSITORY_PREFIX/$service:$IMAGE_TAG"
  else
    image="$IMAGE_REPOSITORY_PREFIX/$service:$IMAGE_TAG"
  fi
  sbom_file="$SBOM_DIR/$service-$IMAGE_TAG.spdx.json"
  if [[ "$BUILD_IMAGES" == "true" ]]; then
    docker build -f "$ROOT_DIR/$service/Dockerfile" -t "$image" "$ROOT_DIR"
  fi
  syft "$image" -o "$SBOM_FORMAT=$sbom_file"
done

echo "SBOM files written to $SBOM_DIR"
