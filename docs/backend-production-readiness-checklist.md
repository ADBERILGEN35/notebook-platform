# Backend production readiness checklist (Faz 124)

Release-candidate verification for backend security controls added in phases 90–123.  
Full review: [`backend-production-readiness-review.md`](backend-production-readiness-review.md).

| # | Area | Control | Repo default / CI | Staging evidence | Prod gate |
|---|------|---------|-------------------|------------------|-----------|
| 1 | Auth/JWT | JWKS, refresh rotation, revoke-all | DONE | Standard auth smoke | JWT keys in secret manager |
| 2 | Gateway admin | Allowlist + RBAC enforce optional | `GATEWAY_ADMIN_*` per env | Enterprise status 200 | Pin `ADMIN_RBAC_ENABLED` rollout |
| 3 | Admin-write MFA | Step-up for writes | Chart + gateway enforce modes | Staging MFA warn/observe | Enforce MFA prod |
| 4 | Break-glass | Disabled by default | `BREAK_GLASS_ENABLED=false` | Runbook enable | Security approval |
| 5 | BG revocation | Denylist + revoke API | `BREAK_GLASS_REVOCATION_ENABLED=false` | [Drill](break-glass-revocation-staging-drill.md) | Drill + denylist on |
| 6 | SCIM provision | Standard SCIM contract | Unchanged | SCIM smoke | IdP cutover runbook |
| 7 | SCIM delta POC | Dry-run only, no scheduler | All delta flags `false` | [Sandbox evidence](scim-delta-sandbox-evidence.md) | Per-provider cert |
| 8 | Retention governance | Platform plan dry-run | `platformRetentionGovernanceEnabled=false` | Gateway smoke fixtures | Legal review |
| 9 | Retention datasource | Dedicated DB optional | `retentionDatasource.*.enabled=false` | [E2E checklist](../scripts/retention/staging-dedicated-retention-e2e-checklist.md) | DBA + overlay |
| 10 | GitOps PR | Mock provider default | `adminGitopsProvider=mock` | Real provider staging | GitHub App + approvals |
| 11 | RBAC visibility | Overrides file optional | Reload off default | Mounted file test | Fail-closed prod |
| 12 | Audit | DB events + export redaction | Partial | Export machine JWT staging | Archive policy |
| 13 | Secrets | No hardcoded creds | `check-no-secrets.sh` + CI | ExternalSecret overlays | Secret rotation |
| 14 | Metrics/alerts | Low-cardinality labels | Dashboards + rules exist | Grafana review | Alertmanager wire |
| 15 | Runbooks | Break-glass, retention, SCIM | Docs linked | Tabletop | On-call sign-off |
| 16 | CI workflows | Fixtures + dispatch gates | 4 readiness workflows | Live dispatch | Attach artifacts to CR |
| 17 | Pre-prod bundle | Aggregate PP-1..PP-3 | `ci-backend-preprod-evidence-bundle.sh` | `backend-preprod-evidence-bundle.yml` dispatch | [`approval gate`](backend-production-approval-gate.md) |
| 18 | RC sign-off package | Freeze + GO decision | `ci-generate-backend-rc-signoff-template.sh` | Manual sign-off from artifacts | [`RC sign-off`](backend-release-candidate-signoff.md) |
| 19 | Release freeze | No post-freeze features / flags | Manual + GitOps review | Freeze checklist signed | [`freeze checklist`](backend-release-freeze-checklist.md) |

## Dangerous defaults (must stay false until approved)

Verified in CI via `scripts/security/ci-backend-production-readiness-review.sh`:

- `BREAK_GLASS_ENABLED`, `BREAK_GLASS_REVOCATION_ENABLED`, `GATEWAY_BREAK_GLASS_*` (except explicit rollout)
- `SCIM_DELTA_PROVIDER_POC_ENABLED`, `SCIM_DELTA_REMOTE_FETCH_ENABLED`, `SCIM_DELTA_REMOTE_MULTI_PAGE_ENABLED`
- `retentionDatasource.*.enabled`
- `adminGitopsPrEnabled`, destructive purge frontend flags
- `FRONTEND_SW_BACKGROUND_SYNC_*` dry-run / disabled

## Required staging evidence (pre-prod blockers)

| Evidence | Workflow / script | Status in repo CI |
|----------|-------------------|-------------------|
| Retention staging smoke | `retention-readiness.yml` dispatch | Skip without secrets (OK) |
| SCIM delta sandbox | `scim-delta-readiness.yml` dispatch | Skip without secrets (OK) |
| Break-glass revocation drill | `break-glass-revocation-readiness.yml` dispatch | Skip without secrets (OK) |

Operators must run live staging jobs and attach artifacts before production enablement of the corresponding flags.

## Pre-prod evidence bundle (Faz 125)

| Step | Action |
|------|--------|
| 1 | Run live staging workflows; download `scim-delta-sandbox-evidence.json`, `break-glass-revocation-evidence.json`, `retention-staging-smoke-results.json` (if PP-3 in scope) |
| 2 | Build bundle: `bash scripts/security/build-backend-preprod-evidence-bundle.sh` or GitHub **Backend Pre-prod Evidence Bundle** (`workflow_dispatch`) |
| 3 | Attach `backend-preprod-evidence-summary.md` + `backend-preprod-evidence-bundle.json` to CR |
| 4 | Confirm `finalRecommendation` is `GO` or signed `GO_WITH_ACCEPTED_RISKS` before flag flip |

Format: [`backend-preprod-evidence-bundle.md`](backend-preprod-evidence-bundle.md).

## Production flag flip (Faz 126 — after bundle GO)

| Step | Action |
|------|--------|
| 1 | Select wave from [`backend-production-flag-flip-plan.md`](backend-production-flag-flip-plan.md) |
| 2 | Complete [`backend-production-change-request-template.md`](backend-production-change-request-template.md) |
| 3 | GitOps PR: one wave; `bash scripts/security/validate-production-flag-plan.sh` on branch |
| 4 | Post-sync smoke + 24h monitoring per plan |
| 5 | Rollback per [`backend-production-rollback-matrix.md`](backend-production-rollback-matrix.md) if needed |

**Repo prod values remain disabled** until approved CRs merge.

## Backend RC readiness gate (Faz 127)

| Step | Action |
|------|--------|
| 1 | Run `bash scripts/security/ci-backend-rc-readiness.sh` or GitHub **Backend RC Readiness** workflow |
| 2 | Confirm verdict `PASS` (or `PASS_WITH_ENVIRONMENT_SKIPS` with documented helm/gradle skips only) |
| 3 | Attach `backend-rc-readiness-summary.md` to release ticket |
| 4 | Run `docker-ci` / full `./gradlew check` separately before production deploy |

PR workflow runs lightweight gate (`RC_SKIP_GRADLE=true`); `main` / `workflow_dispatch` runs full targeted Gradle + Helm.

## Backend Docker CI (Faz 128 — full environment)

| Step | Action |
|------|--------|
| 1 | After RC gate `PASS`, run **Backend Docker CI** on `main` or `workflow_dispatch` |
| 2 | Confirm verdict `PASS` (not `ENVIRONMENT_SKIPPED` on CI) |
| 3 | Attach `backend-docker-ci-summary.md` to release ticket |
| 4 | Local without Docker: `BACKEND_DOCKER_ALLOW_SKIP=true bash scripts/security/ci-backend-docker-check.sh` documents skip only |

Not required on every PR (heavy). Optional label/manual dispatch for pre-release branches.

## Docker CI green (Faz 129)

| Step | Status |
|------|--------|
| `:api-gateway:spotlessCheck` | **PASS** |
| `ci-backend-docker-check.sh` (full `check` + `rlsIntegrationTest`) | **PASS** |
| `ci-backend-rc-readiness.sh` | **PASS_WITH_ENVIRONMENT_SKIPS** (helm local skip) |
| Production GitOps flags | **unchanged** |

## Backend RC sign-off + freeze (Faz 131)

| Step | Action |
|------|--------|
| 1 | Complete [freeze checklist](backend-release-freeze-checklist.md) at RC freeze |
| 2 | Attach RC, Docker CI, and live PP bundle artifacts for RC SHA |
| 3 | Generate sign-off: `bash scripts/security/generate-backend-rc-signoff-template.sh --rc-id <id> --output signoff.md` |
| 4 | Record final decision per [sign-off rules](backend-release-candidate-signoff.md#final-decision-rules) |
| 5 | Only after `GO` or signed `GO_WITH_ACCEPTED_RISKS`, open flag-flip CRs |

Template default is **NO_GO** until live evidence is attached.

## Live staging PP evidence run (Faz 132)

| Step | Action |
|------|--------|
| 1 | `bash scripts/security/check-staging-pp-secrets.sh` |
| 2 | Dispatch PP workflows or `run-backend-live-staging-pp-evidence.sh --run-pp1 --run-pp2` |
| 3 | Confirm PP-1 `certified`, PP-2 `passed` + `BREAK_GLASS_TOKEN_REVOKED` |
| 4 | Build bundle; verify `finalRecommendation` |
| 5 | Update RC sign-off from bundle JSON |

Checklist: [`backend-live-staging-pp-evidence-run-checklist.md`](backend-live-staging-pp-evidence-run-checklist.md).
