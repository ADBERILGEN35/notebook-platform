# Supply Chain Security

Faz 17 adds pre-registry image quality gates. The pipeline builds local Docker images, generates
SBOM artifacts and scans images before any publish/deploy step exists.

## Tools

- SBOM: Syft.
- Image vulnerability scan: Trivy.
- Output: `sbom/*.spdx.json`.

Generated SBOMs are CI artifacts and are ignored by git. They are intentionally not committed
because they are build outputs tied to a specific image tag and dependency graph.

## Scripts

```bash
scripts/security/generate-sbom.sh
scripts/security/scan-images.sh
```

Defaults:

- `IMAGE_PREFIX=notebook-platform`
- `IMAGE_TAG=phase17-local`
- `BUILD_IMAGES=true`
- `HIGH_EXIT_CODE=0`

`ALLOW_SECURITY_TOOL_SKIP=true` may be used for local developer machines where Syft, Trivy or Docker
are not installed. CI does not set this flag; missing tools fail the gate.

## CI Policy

The CI gate:

1. runs existing formatting, secret, Helm, Gradle check and bootJar gates
2. installs Syft and Trivy
3. builds local images without registry push
4. generates SPDX JSON SBOMs
5. uploads SBOMs as artifacts
6. fails on CRITICAL vulnerabilities
7. warns on HIGH vulnerabilities by default

HIGH findings start as warn-only because this is the first image scan baseline and dependencies may
include inherited OS/JRE findings that need triage. The intended hardening path is:

1. review HIGH findings weekly
2. document accepted risk or fix/upgrade
3. add a `.trivyignore` entry only with owner, reason and expiry
4. switch `HIGH_EXIT_CODE=1` after the baseline is clean

## False Positives

Ignore policy:

- Prefer package upgrades or base image refresh over ignores.
- Ignore entries must include the CVE, affected image, reason, owner and expiry date.
- Expired ignores should fail review even if the scanner still supports them.

No registry signing, provenance attestation or real registry scanning is added in this phase.
