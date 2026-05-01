#!/usr/bin/env bash
set -euo pipefail

IMAGE_REGISTRY="${IMAGE_REGISTRY:-}"
IMAGE_REPOSITORY_PREFIX="${IMAGE_REPOSITORY_PREFIX:-${IMAGE_PREFIX:-notebook-platform}}"
IMAGE_TAG="${IMAGE_TAG:-phase21-local}"
SIGNING_MODE="${SIGNING_MODE:-keyless}" # keyless | key
COSIGN_KEY="${COSIGN_KEY:-}"
COSIGN_EXPERIMENTAL="${COSIGN_EXPERIMENTAL:-true}"
COSIGN_YES="${COSIGN_YES:-true}"
ALLOW_SECURITY_TOOL_SKIP="${ALLOW_SECURITY_TOOL_SKIP:-false}"
COSIGN_DRY_RUN="${COSIGN_DRY_RUN:-true}"
SERVICES=(api-gateway identity-service workspace-service content-service)

if [[ -z "$IMAGE_REGISTRY" ]]; then
  echo "IMAGE_REGISTRY is required for signing because cosign signs registry image references." >&2
  if [[ "$ALLOW_SECURITY_TOOL_SKIP" == "true" ]]; then
    echo "Skipping image signing because ALLOW_SECURITY_TOOL_SKIP=true." >&2
    exit 0
  fi
  exit 1
fi

if [[ "$COSIGN_DRY_RUN" != "true" ]] && ! command -v cosign >/dev/null 2>&1; then
  if [[ "$ALLOW_SECURITY_TOOL_SKIP" == "true" ]]; then
    echo "cosign is not installed; skipping image signing." >&2
    exit 0
  fi
  echo "cosign is required. Install Cosign or set ALLOW_SECURITY_TOOL_SKIP=true for local-only skips." >&2
  exit 1
fi

if [[ "$COSIGN_DRY_RUN" != "true" && "$SIGNING_MODE" == "key" && -z "$COSIGN_KEY" ]]; then
  echo "COSIGN_KEY is required when SIGNING_MODE=key." >&2
  exit 1
fi

for service in "${SERVICES[@]}"; do
  image="$IMAGE_REGISTRY/$IMAGE_REPOSITORY_PREFIX/$service:$IMAGE_TAG"
  if [[ "$COSIGN_DRY_RUN" == "true" ]]; then
    echo "Dry run: would sign $image with SIGNING_MODE=$SIGNING_MODE"
    continue
  fi

  if [[ "$SIGNING_MODE" == "key" ]]; then
    cosign sign --yes="$COSIGN_YES" --key "$COSIGN_KEY" "$image"
  elif [[ "$SIGNING_MODE" == "keyless" ]]; then
    COSIGN_EXPERIMENTAL="$COSIGN_EXPERIMENTAL" cosign sign --yes="$COSIGN_YES" "$image"
  else
    echo "Unsupported SIGNING_MODE=$SIGNING_MODE; expected keyless or key." >&2
    exit 1
  fi
done
