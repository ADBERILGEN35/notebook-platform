# GitOps Deployment

Faz 20 defines a provider-agnostic GitOps deployment model for the existing Helm chart. It does not
install Argo CD or Flux, deploy to a real cluster, push images to a real registry or apply runtime
RLS to production.

## Tooling Decision

Argo CD is the primary example because it gives clear Application lifecycle visibility, supports
Helm charts directly and is easy to operate with a dev/staging/prod Application split. The example
manifests are under `deploy/gitops/argocd`.

Flux remains a valid alternative for teams that prefer a lighter Git-native controller and do not
need a UI-first application view. A Flux implementation should use the same environment values and
promotion rules defined here.

## Repository Layout

```text
deploy/gitops/
  README.md
  argocd/
    app-project.yaml
    app-dev.yaml
    app-staging.yaml
    app-prod.yaml
  environments/
    dev/values.yaml
    staging/values.yaml
    prod/values.yaml
```

The mono-repo layout is enough for the current platform. A separate GitOps repository can be used
later if production change control requires tighter permissions or a different approval path.

## Environment Strategy

`dev` is optimized for fast feedback:

- single replicas
- autoscaling disabled
- NetworkPolicy disabled
- `serviceMonitor.enabled=false`
- existing Kubernetes Secret placeholder
- `INTERNAL_AUTH_MODE=dual`
- `APP_RLS_ENABLED=false`
- `APP_RLS_STRICT_WORKSPACE_HEADER=false`

`staging` is the promotion proving ground:

- one or two replicas
- NetworkPolicy enabled
- External Secrets mode
- ServiceMonitor enabled
- `INTERNAL_AUTH_MODE=service-jwt`
- `APP_RLS_STRICT_WORKSPACE_HEADER=true`
- `APP_RLS_ENABLED=false` until the RLS rollout stage explicitly enables tenant context

`prod` is conservative:

- two or more replicas
- NetworkPolicy enabled
- External Secrets mode
- ServiceMonitor enabled
- HPA enabled with CPU targets
- `INTERNAL_AUTH_MODE=service-jwt`
- RLS flags are tied to the rollout stage, not changed automatically

FORCE RLS is never applied by GitOps auto-sync. Use the DBA/ops SQL scripts and the staged rollout
in `docs/runtime-rls-rollout.md`.

## Image Tagging

Do not use `latest`.

- Merge to `main`: build immutable images tagged with the git SHA.
- Release tag: build semantic version images such as `v1.2.3`.
- Environment promotion: update `deploy/gitops/environments/<env>/values.yaml` image tags.
- Production promotion should prefer digest pinning once registry digest capture is wired into the
  release workflow.

The Helm chart supports per-service `services.<service>.image.tag` overrides and a shared
`global.imageRegistry`. Faz 21 also adds optional `services.<service>.image.digest`; when set, Helm
renders `repository@sha256:...` instead of `repository:tag`.

## CI/CD Draft

`.github/workflows/release-images.yml` is a manual draft pipeline:

1. Checkout and Java 25 setup.
2. `spotlessCheck`, `clean check` and `bootJar`.
3. Secret scan.
4. Helm render validation.
5. Docker image build.
6. SBOM generation with Syft.
7. Image scan with Trivy.
8. Optional registry login and push when `push_images=true` and `dry_run=false`.
9. Optional Cosign image signing when `sign_images=true`.
10. Optional provenance placeholder or CI attestation when `generate_provenance=true`.

Required secret placeholders when push is enabled:

- `REGISTRY_USERNAME`
- `REGISTRY_PASSWORD`

Optional key-based signing placeholder:

- `COSIGN_KEY`

Keyless signing is the preferred model and uses GitHub Actions OIDC rather than a long-lived signing
key.

`.github/workflows/gitops-promote.yml` is a manual promotion draft. It updates the selected
environment values file to an immutable image tag and, when `dry_run=false`, opens a promotion PR
using `GH_TOKEN`.

## Promotion

Recommended flow:

1. Main branch merge creates git-SHA image tags after quality gates pass.
2. Dev tracks the validated tag and can auto-sync.
3. Staging promotion updates `deploy/gitops/environments/staging/values.yaml`.
4. Run smoke validation against staging:
   - `scripts/smoke-test.sh`
   - `scripts/smoke-test-auth-security.sh`
   - `scripts/smoke-test-rls-strict.sh` when the RLS stage requires strict header validation
5. Production promotion is a PR that changes only prod values and requires manual approval.
6. Prod promotion requires SBOM artifact upload, CRITICAL-free scan result, signed image status and
   provenance readiness.

For RLS:

- Stage 1 strict header validation runs in staging first.
- Stage 2 tenant context enablement runs in staging before production.
- `./gradlew rlsIntegrationTest` should pass before staging values move beyond Stage 1.
- Capture deployed staging evidence with `docs/rls-staging-validation-report-template.md`.
- FORCE RLS remains an explicit database operation outside GitOps auto-sync.

## Post-Sync Validation

`scripts/gitops-post-sync-check.sh` requires `BASE_URL` and performs a health check. Optional flags
run existing smoke tests:

```bash
BASE_URL=https://api.staging.example.com RUN_SMOKE_TEST=true bash scripts/gitops-post-sync-check.sh
BASE_URL=https://api.staging.example.com RUN_AUTH_SECURITY_SMOKE=true bash scripts/gitops-post-sync-check.sh
BASE_URL=https://api.staging.example.com RUN_RLS_STRICT_SMOKE=true bash scripts/gitops-post-sync-check.sh
```

The script can be run manually after Argo CD sync or wrapped in a PostSync hook later. The hook
manifest is intentionally not added in this phase because it depends on cluster RBAC and image
policy choices.

## Rollback

Use the smallest rollback that matches the failure.

Argo CD rollback:

- Use when the rendered Kubernetes resources from a recent sync are bad.
- Risk: it reverts manifests, not database state.
- Validate with `BASE_URL=<url> bash scripts/gitops-post-sync-check.sh`.

Image tag revert PR:

- Use when the deployment artifact is bad but config is valid.
- Risk: API contract changes such as pagination response envelopes may not be backward compatible
  with older clients.
- Validate smoke and auth-security scripts.

Digest pin revert PR:

- Use when a tag was valid but the promoted digest points to a bad artifact.
- Risk: digest pinning is exact, so rollback requires a known-good digest per service.
- Validate Cosign verification and smoke tests before merge.

Values/config revert:

- Use when a bad environment flag or resource setting caused the issue.
- Risk: changing RLS or internal auth flags can affect live requests.
- Validate service health, auth flow and internal workspace/content contract behavior.

RLS rollback:

- Set `APP_RLS_STRICT_WORKSPACE_HEADER=false` or `APP_RLS_ENABLED=false` only as a controlled stage
  rollback.
- FORCE RLS disable scripts are database operations and must be executed by ops/DBA, not GitOps
  auto-sync.
- Validate with the RLS strict smoke script and targeted database preflight checks.

Helm previous revision:

- Useful only when deploying manually outside GitOps.
- Risk: Git remains the source of truth, so reconcile the Git state immediately after rollback.

## Security

- Do not commit secrets to GitOps values.
- Use External Secrets for staging/prod.
- Keep prod sync manual or approval-gated.
- Separate namespaces for dev, staging and prod.
- Enable NetworkPolicy in staging/prod.
- Use immutable image tags.
- Prefer digest-pinned prod image references after registry digest capture is stable.
- Treat SBOM generation and Trivy critical scan as release gates.
- Do not promote unsigned images to prod except through a documented emergency exception.
- Do not expose actuator endpoints through public ingress.
- Do not apply FORCE RLS from automated GitOps sync.
- Add real cluster admission enforcement in a later hardening phase.

## Environment Readiness Checklist

Dev:

- Local/test secret strategy exists.
- DB and Redis are reachable.
- Basic smoke test passes.

Staging:

- External Secrets controller and SecretStore are configured.
- JWKS is reachable.
- Service JWT validation is enabled.
- NetworkPolicy is enabled and verified.
- ServiceMonitor is scraping metrics.
- SBOM and image scan gates passed.
- Cosign signing and verification dry-run or audit validation passed.
- RLS Stage 1 strict smoke passed before moving to later stages.

Prod:

- Manual approval is required.
- Image tag is immutable.
- Image signature and provenance status are recorded.
- Optional image digest is captured for each service.
- External Secrets are configured.
- NetworkPolicy is enabled.
- Resource requests support HPA.
- Grafana dashboards and Prometheus alert rules are loaded.
- Rollback has been tested.
- DB backup/restore plan exists.
- RLS rollout stage is explicitly approved.
