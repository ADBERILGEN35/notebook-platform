#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
IMAGE_REGISTRY="${IMAGE_REGISTRY:-}"
IMAGE_REPOSITORY_PREFIX="${IMAGE_REPOSITORY_PREFIX:-${IMAGE_PREFIX:-notebook-platform}}"
IMAGE_TAG="${IMAGE_TAG:-phase21-local}"
PROVENANCE_DIR="${PROVENANCE_DIR:-$ROOT_DIR/provenance}"
PROVENANCE_MODE="${PROVENANCE_MODE:-placeholder}" # placeholder | github-attestation
ALLOW_SECURITY_TOOL_SKIP="${ALLOW_SECURITY_TOOL_SKIP:-false}"
SERVICES=(api-gateway identity-service workspace-service content-service)

mkdir -p "$PROVENANCE_DIR"

if [[ "$PROVENANCE_MODE" == "github-attestation" ]]; then
  if [[ -z "${GITHUB_ACTIONS:-}" ]]; then
    echo "GitHub artifact attestation requires GitHub Actions runtime." >&2
    if [[ "$ALLOW_SECURITY_TOOL_SKIP" == "true" ]]; then
      echo "Skipping provenance generation because ALLOW_SECURITY_TOOL_SKIP=true." >&2
      exit 0
    fi
    exit 1
  fi
  echo "Use actions/attest-build-provenance in the workflow for registry-bound provenance attestations."
  exit 0
fi

for service in "${SERVICES[@]}"; do
  if [[ -n "$IMAGE_REGISTRY" ]]; then
    image="$IMAGE_REGISTRY/$IMAGE_REPOSITORY_PREFIX/$service:$IMAGE_TAG"
  else
    image="$IMAGE_REPOSITORY_PREFIX/$service:$IMAGE_TAG"
  fi

  cat >"$PROVENANCE_DIR/$service-$IMAGE_TAG.provenance.placeholder.json" <<EOF
{
  "_note": "Placeholder provenance generated outside a trusted CI attestation context.",
  "predicateType": "https://slsa.dev/provenance/v1",
  "subject": {
    "name": "$image",
    "digest": "sha256:PLACEHOLDER"
  },
  "builder": {
    "id": "local-placeholder"
  },
  "buildType": "notebook-platform-local-placeholder"
}
EOF
done

echo "Placeholder provenance files written to $PROVENANCE_DIR"
