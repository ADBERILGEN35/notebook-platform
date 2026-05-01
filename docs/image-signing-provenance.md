# Image Signing and Provenance

Faz 21 prepares image signing, provenance and SBOM enforcement without requiring a real registry,
real signing key, cloud KMS or cluster admission controller.

## Recommended Strategy

Primary: Cosign keyless signing.

- Uses GitHub Actions OIDC identity.
- Avoids long-lived private signing keys in CI.
- Can publish transparency log entries through Rekor.
- Works well with admission policies that trust the workflow identity.

Alternative: Cosign key-based signing.

- Uses `COSIGN_KEY` for signing and `COSIGN_PUBLIC_KEY` for verification.
- Fits offline or enterprise environments where keyless Fulcio/Rekor access is not allowed.
- Requires private key storage in a secret manager or KMS.
- Rotation and access auditing become operational responsibilities.

## Image Reference Model

Each service image should use an immutable tag and may later be promoted by digest:

```text
registry.example.com/notebook-platform/api-gateway:<git-sha>
registry.example.com/notebook-platform/api-gateway@sha256:<digest>
```

The Helm chart supports both:

- `services.<service>.image.tag`
- `services.<service>.image.digest`

When `digest` is set, Helm renders `repository@sha256:...`. When it is empty, Helm renders
`repository:tag`.

## Signing

Dry-run or local validation:

```bash
IMAGE_REGISTRY=registry.example.com \
IMAGE_REPOSITORY_PREFIX=notebook-platform \
IMAGE_TAG=<git-sha> \
COSIGN_DRY_RUN=true \
bash scripts/security/sign-images.sh
```

Keyless signing in GitHub Actions:

```bash
IMAGE_REGISTRY=registry.example.com \
IMAGE_REPOSITORY_PREFIX=notebook-platform \
IMAGE_TAG=<git-sha> \
SIGNING_MODE=keyless \
COSIGN_DRY_RUN=false \
bash scripts/security/sign-images.sh
```

Key-based signing:

```bash
IMAGE_REGISTRY=registry.example.com \
IMAGE_REPOSITORY_PREFIX=notebook-platform \
IMAGE_TAG=<git-sha> \
SIGNING_MODE=key \
COSIGN_KEY=/path/to/cosign.key \
COSIGN_DRY_RUN=false \
bash scripts/security/sign-images.sh
```

Do not commit signing keys. Use CI secrets, KMS or a secret manager when key-based signing is
required.

## Verification

Keyless verification requires the expected OIDC issuer and workflow identity:

```bash
IMAGE_REGISTRY=registry.example.com \
IMAGE_REPOSITORY_PREFIX=notebook-platform \
IMAGE_TAG=<git-sha> \
VERIFY_MODE=keyless \
COSIGN_CERTIFICATE_ISSUER=https://token.actions.githubusercontent.com \
COSIGN_CERTIFICATE_IDENTITY=https://github.com/ORG/REPO/.github/workflows/release-images.yml@refs/heads/main \
COSIGN_DRY_RUN=false \
bash scripts/security/verify-images.sh
```

Key-based verification:

```bash
IMAGE_REGISTRY=registry.example.com \
IMAGE_REPOSITORY_PREFIX=notebook-platform \
IMAGE_TAG=<git-sha> \
VERIFY_MODE=key \
COSIGN_PUBLIC_KEY=/path/to/cosign.pub \
COSIGN_DRY_RUN=false \
bash scripts/security/verify-images.sh
```

## Provenance

Target provenance model:

- SLSA provenance predicate.
- Subject is the pushed image digest.
- Builder is the GitHub Actions workflow identity.
- Attestation is pushed to the registry or stored with release artifacts.

The workflow includes `actions/attest-build-provenance` hooks for pushed images. Local runs use
`scripts/security/generate-provenance.sh` to create placeholder files only, because local machines
do not provide a trusted CI OIDC identity or immutable registry digest by default.

## SBOM Relationship

SBOMs are generated per service and tied to the image tag:

- `sbom/api-gateway-${IMAGE_TAG}.spdx.json`
- `sbom/identity-service-${IMAGE_TAG}.spdx.json`
- `sbom/workspace-service-${IMAGE_TAG}.spdx.json`
- `sbom/content-service-${IMAGE_TAG}.spdx.json`

SPDX JSON remains the primary format because the existing Syft script already produces it. The SBOM
artifact should be uploaded by CI and retained with the release. Registry-attached SBOMs can be added
later with Cosign attestations or OCI artifact storage.

## Admission Enforcement

Faz 21 adds examples only:

- `deploy/policies/cosign-clusterimagepolicy.example.yaml`
- `deploy/policies/kyverno-require-signed-images.example.yaml`
- `deploy/policies/kyverno-require-non-latest-tag.example.yaml`
- `deploy/policies/kyverno-require-non-root.example.yaml`

Recommended rollout:

1. Audit-only admission policy in staging.
2. Fix false positives and image reference gaps.
3. Enforce signature and non-latest checks in staging.
4. Promote prod policy in audit mode.
5. Enforce prod only after rollback is tested.

## Exceptions

Exceptions should be time-boxed and documented:

- image reference
- reason
- owner
- expiry date
- compensating validation

Unsigned images should not be promoted to prod unless an incident commander approves a temporary
exception and a rollback path is documented.
