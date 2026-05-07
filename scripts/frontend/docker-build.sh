#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
FRONTEND_IMAGE="${FRONTEND_IMAGE:-notebook-platform/frontend}"
IMAGE_TAG="${IMAGE_TAG:-local}"

docker build \
  -f "$ROOT_DIR/frontend/Dockerfile" \
  -t "$FRONTEND_IMAGE:$IMAGE_TAG" \
  "$ROOT_DIR/frontend"

echo "Built image: $FRONTEND_IMAGE:$IMAGE_TAG"
