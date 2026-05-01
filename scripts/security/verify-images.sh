#!/usr/bin/env bash
set -euo pipefail

IMAGE_REGISTRY="${IMAGE_REGISTRY:-}"
IMAGE_REPOSITORY_PREFIX="${IMAGE_REPOSITORY_PREFIX:-${IMAGE_PREFIX:-notebook-platform}}"
IMAGE_TAG="${IMAGE_TAG:-phase21-local}"
VERIFY_MODE="${VERIFY_MODE:-keyless}" # keyless | key
COSIGN_PUBLIC_KEY="${COSIGN_PUBLIC_KEY:-}"
COSIGN_CERTIFICATE_ISSUER="${COSIGN_CERTIFICATE_ISSUER:-https://token.actions.githubusercontent.com}"
COSIGN_CERTIFICATE_IDENTITY="${COSIGN_CERTIFICATE_IDENTITY:-https://github.com/ORG/REPO/.github/workflows/release-images.yml@refs/heads/main}"
ALLOW_SECURITY_TOOL_SKIP="${ALLOW_SECURITY_TOOL_SKIP:-false}"
COSIGN_DRY_RUN="${COSIGN_DRY_RUN:-true}"
SERVICES=(api-gateway identity-service workspace-service content-service)

if [[ -z "$IMAGE_REGISTRY" ]]; then
  echo "IMAGE_REGISTRY is required for verification because cosign verifies registry image references." >&2
  if [[ "$ALLOW_SECURITY_TOOL_SKIP" == "true" ]]; then
    echo "Skipping image verification because ALLOW_SECURITY_TOOL_SKIP=true." >&2
    exit 0
  fi
  exit 1
fi

if [[ "$COSIGN_DRY_RUN" != "true" ]] && ! command -v cosign >/dev/null 2>&1; then
  if [[ "$ALLOW_SECURITY_TOOL_SKIP" == "true" ]]; then
    echo "cosign is not installed; skipping image verification." >&2
    exit 0
  fi
  echo "cosign is required. Install Cosign or set ALLOW_SECURITY_TOOL_SKIP=true for local-only skips." >&2
  exit 1
fi

if [[ "$COSIGN_DRY_RUN" != "true" && "$VERIFY_MODE" == "key" && -z "$COSIGN_PUBLIC_KEY" ]]; then
  echo "COSIGN_PUBLIC_KEY is required when VERIFY_MODE=key." >&2
  exit 1
fi

for service in "${SERVICES[@]}"; do
  image="$IMAGE_REGISTRY/$IMAGE_REPOSITORY_PREFIX/$service:$IMAGE_TAG"
  if [[ "$COSIGN_DRY_RUN" == "true" ]]; then
    echo "Dry run: would verify $image with VERIFY_MODE=$VERIFY_MODE"
    continue
  fi

  if [[ "$VERIFY_MODE" == "key" ]]; then
    cosign verify --key "$COSIGN_PUBLIC_KEY" "$image"
  elif [[ "$VERIFY_MODE" == "keyless" ]]; then
    cosign verify \
      --certificate-identity "$COSIGN_CERTIFICATE_IDENTITY" \
      --certificate-oidc-issuer "$COSIGN_CERTIFICATE_ISSUER" \
      "$image"
  else
    echo "Unsupported VERIFY_MODE=$VERIFY_MODE; expected keyless or key." >&2
    exit 1
  fi
done
