# Supply Chain Security

Faz 17 added pre-registry image quality gates. Faz 21 extends that model with signing,
provenance-ready workflows, release SBOM naming and admission policy examples.

Target release chain:

1. source code commit
2. CI build
3. tests
4. Docker image build
5. SBOM generation
6. vulnerability scan
7. image signing
8. provenance attestation
9. registry push and digest capture
10. GitOps values image tag or digest update
11. cluster admission validation

In practice, registry-backed signing and attestation need a pushed image reference or digest. The
workflow keeps dry-run defaults and performs real signing/provenance only when push/sign inputs are
explicitly enabled.

## Tools

- SBOM: Syft.
- Image vulnerability scan: Trivy.
- Image signing and verification: Cosign.
- Provenance: SLSA-style provenance through GitHub artifact attestations when running in CI.
- Output: `sbom/*.spdx.json`.

Generated SBOMs are CI artifacts and are ignored by git. They are intentionally not committed
because they are build outputs tied to a specific image tag and dependency graph.

## Scripts

```bash
scripts/security/generate-sbom.sh
scripts/security/scan-images.sh
scripts/security/sign-images.sh
scripts/security/verify-images.sh
scripts/security/generate-provenance.sh
```

Defaults:

- `IMAGE_REGISTRY=<empty>`
- `IMAGE_REPOSITORY_PREFIX=notebook-platform`
- `IMAGE_TAG=phase17-local`
- `BUILD_IMAGES=true`
- `HIGH_EXIT_CODE=0`
- `MEDIUM_LOW_EXIT_CODE=0`
- `SIGNING_MODE=keyless`
- `VERIFY_MODE=keyless`
- `COSIGN_DRY_RUN=true`

`ALLOW_SECURITY_TOOL_SKIP=true` may be used for local developer machines where Syft, Trivy or Docker
are not installed. CI does not set this flag; missing tools fail the gate.

## SBOM Artifacts

SPDX JSON is the primary SBOM format.

Artifact naming:

- `sbom/api-gateway-${IMAGE_TAG}.spdx.json`
- `sbom/identity-service-${IMAGE_TAG}.spdx.json`
- `sbom/workspace-service-${IMAGE_TAG}.spdx.json`
- `sbom/content-service-${IMAGE_TAG}.spdx.json`

Read SBOMs by service first, then inspect packages by ecosystem and version. During CVE triage,
connect each finding to:

- affected service image
- package name/version
- CVE severity and exploitability
- available fixed version
- whether the finding is reachable in this service
- owner and due date

SBOMs are CI release artifacts and should be retained with the release tag. They are ignored by git
because they are build outputs.

## CI Policy

The CI gate:

1. runs existing formatting, secret, Helm, Gradle check and bootJar gates
2. installs Syft and Trivy
3. builds local images without registry push
4. generates per-service SPDX JSON SBOMs
5. scans images with Trivy
6. uploads SBOMs as artifacts
7. optionally pushes images when `push_images=true` and `dry_run=false`
8. optionally signs pushed images with Cosign
9. optionally creates provenance attestations

## Vulnerability Policy

Initial policy:

- CRITICAL: fail
- HIGH: warn
- MEDIUM: report only
- LOW: report only

Production target policy:

- CRITICAL: fail
- HIGH: fail after baseline cleanup
- MEDIUM: warn
- LOW: report

HIGH findings start as warn-only because this is the first image scan baseline and dependencies may
include inherited OS/JRE findings that need triage. The intended hardening path is:

1. review HIGH findings weekly
2. document accepted risk or fix/upgrade
3. add a `.trivyignore` entry only with owner, reason and expiry
4. switch `HIGH_EXIT_CODE=1` after the baseline is clean

## False Positives And Ignores

Ignore policy:

- Prefer package upgrades or base image refresh over ignores.
- Ignore entries must include the CVE, affected image, reason, owner and expiry date.
- Expired ignores should fail review even if the scanner still supports them.
- `.trivyignore` may be used only with a linked ticket or documented risk acceptance.
- Ignore files must not become a permanent substitute for dependency or base image upgrades.

## Signing And Provenance

Primary signing strategy: keyless Cosign using GitHub Actions OIDC.

Alternative: key-based Cosign with private key material held in CI secrets, KMS or a secret manager.

Provenance target: SLSA-style build provenance attached to pushed image digests by CI. Local runs
generate placeholder provenance only.

Details: `docs/image-signing-provenance.md`.

## GitOps Release Gate

Faz 20 connects these checks to the release model:

- image tags are immutable git SHAs or semantic release tags
- SBOMs are generated before promotion
- Trivy CRITICAL findings fail the release gate
- HIGH findings remain warn-only until the baseline is clean
- GitOps values are promoted only after the image gate passes

Faz 21 adds signing/provenance-ready workflows and policy examples. Real registry enforcement and
cluster admission enforcement remain future work.
