# Backend production readiness — final security review (Faz 124)

**Date:** 2026-05-17  
**Scope:** Backend services (identity, gateway, workspace, content, notification, search), Helm/GitOps defaults, observability assets, security scripts/workflows.  
**Verdict:** **READY_WITH_PREPROD_BLOCKERS** — architecture and repo defaults are production-safe; **live staging evidence** for retention datasource, SCIM delta sandbox, and break-glass revocation drill is still required before enabling corresponding production flags.

---

## Executive summary

Phases 90–123 added break-glass access and revocation, SCIM delta diagnostics, platform retention governance, dedicated retention datasources, admin RBAC/GitOps, and evidence-gated staging workflows. This review confirms:

- Chart **production defaults remain conservative** (verified by new CI script).
- **No committed secrets** in docs/deploy/scripts/workflows/examples (spot check + `check-no-secrets.sh`).
- **Targeted backend tests pass** for BreakGlass, Scim, and Admin surfaces.
- **Three pre-production blockers** are operational (missing live staging artifacts), not code defects.

Backend is **not NOT_READY** for release packaging; operators must complete staging evidence gates before flipping high-risk flags in production.

---

## Scope

| In scope | Out of scope |
|----------|----------------|
| JWT/auth, gateway admin, MFA, break-glass, SCIM delta POC | Frontend Vitest/tsc (noted separately) |
| Retention governance + datasource | Full k6 load testing |
| RBAC, GitOps PR automation, audit | mTLS / service mesh |
| Helm values, GitOps env examples | Production cluster execution |
| Prometheus/Grafana retention + platform alerts | E2E Playwright admin UI |

---

## Passed controls

### Auth / JWT
- Identity-service JWKS, multi-key signing, refresh token rotation and revoke-all (existing phases).
- Gateway JWT validation with `kid`; break-glass `token_type` guard.

### Gateway admin authorization
- Admin allowlist, optional `GATEWAY_ADMIN_RBAC_ENFORCE`, per-route permission checks.
- Enterprise status aggregation; no secret material in JSON responses.

### Admin-write MFA
- Gateway `ensureBreakGlassRevoke`, change-request approve, RBAC reload require MFA when configured.
- Staging may use `gatewayAdminMfaMode: warn` — acceptable for observe rollout, not for prod enforce without promotion.

### Break-glass (Faz 90–94)
- `BREAK_GLASS_ENABLED=false`, static/webauthn/offline modes off by default.
- `BREAK_GLASS_ALLOW_ADMIN_WRITE=false`, `GATEWAY_BREAK_GLASS_ADMIN_ALLOWED=false`.
- Rotation tracking off by default; fingerprints only in DB.

### Break-glass revocation / denylist (Faz 122–123)
- `BREAK_GLASS_REVOCATION_ENABLED=false`, `GATEWAY_BREAK_GLASS_DENYLIST_CHECK_ENABLED=false`.
- Session list/revoke APIs; jti denylist; gateway `BREAK_GLASS_TOKEN_REVOKED`.
- Drill script + workflow; privacy-validated evidence schema.

### SCIM provisioning / delta POC (Faz 115–121)
- `SCIM_DELTA_PROVIDER_POC_ENABLED=false`, `SCIM_DELTA_REMOTE_FETCH_ENABLED=false`, `SCIM_DELTA_REMOTE_MULTI_PAGE_ENABLED=false`.
- `SCIM_DELTA_DRY_RUN_ONLY=true`; no production scheduler; no deprovision-from-missing-delta.
- Evidence validator, certification checklists, staging workflow (`workflow_dispatch` only for live).

### Retention (Faz 105–114)
- `platformRetentionGovernanceEnabled=false`; dry-run counts; legal-hold gates.
- `retentionDatasource.*.enabled=false` in chart and staging GitOps.
- Actuator health without JDBC/PII; staging smoke + fixture matrix in CI.

### GitOps / RBAC (Faz 79–88)
- `adminGitopsPrEnabled=false`, `adminGitopsProvider=mock` (safe default).
- RBAC overrides reload/watch off by default; visibility allowlist configurable.
- Approved change requests required before GitOps PR generation.

### Audit
- Identity audit events; gateway export redaction; SIEM outbox optional.

### Secrets / config
- `check-no-secrets.sh` in CI; SCIM bearer via `secretKeyRef` only; break-glass hash not token in Git.

### Metrics / alerts
- `platform-retention-*` metrics with bounded labels (`service`, `status`, `warning_code`).
- `platform-retention-readiness` dashboard + 3 alert rules; no PII label keys observed.

### CI / workflows
| Workflow | PR/main | Live staging |
|----------|---------|--------------|
| `ci.yml` | spotless, check, secrets, **default flag check** | — |
| `retention-readiness.yml` | fixtures | dispatch + secrets |
| `scim-delta-readiness.yml` | fixtures | dispatch / staging push |
| `break-glass-revocation-readiness.yml` | fixtures | dispatch only |

---

## Open blockers (pre-production)

These are **release-candidate gates for flag enablement**, not blockers to merge the codebase.

| ID | Severity | Item | Resolution |
|----|----------|------|------------|
| PP-1 | **PRE-PROD BLOCKER** | Live **SCIM delta sandbox** evidence per provider (Okta/Entra/generic) | Run `scim-delta-readiness` workflow_dispatch; attach artifacts + signed checklist |
| PP-2 | **PRE-PROD BLOCKER** | Live **break-glass revocation drill** (`result: passed`) | Run `break-glass-revocation-readiness` workflow_dispatch |
| PP-3 | **PRE-PROD BLOCKER** | Live **retention dedicated datasource** E2E (if enabling `retentionDatasource`) | Follow `scripts/retention/staging-dedicated-retention-e2e-checklist.md` |

---

## High risks

| ID | Risk | Mitigation |
|----|------|------------|
| H-1 | RLS runtime enforcement not fully staged in real cluster (`appRlsEnabled=false` in staging values) | Execute RLS stage rollout docs before tenant isolation claims |
| H-2 | Admin MFA in staging set to `warn` — writes may proceed without MFA during observe | Promote to `enforce` before production admin-write reliance |
| H-3 | Break-glass denylist cache TTL (30s) — brief window after revoke | Accept for drill; tune fail-closed + TTL in prod rollout plan |
| H-4 | SCIM remote fetch enabled in staging overlay example only — mis-merge could expose bearer secret name without secret | Code review + ExternalSecret checklist; never commit bearer values |

---

## Medium risks

| ID | Risk | Notes |
|----|------|-------|
| M-1 | Helm template check skipped when `helm` not on developer PATH | CI Ubuntu runs `helm-template-check.sh` |
| M-2 | Full `./gradlew check` includes long suite; targeted tests used for this review | CI still runs full `check` on PR |
| M-3 | Testcontainers/RLS integration tests require Docker — not part of Faz 124 run | Documented in CLAUDE.md |
| M-4 | GitOps `adminGitopsProvider=mock` — production needs real VCS integration | Enable only with PAT/GitHub App + branch protection |
| M-5 | SIEM push optional — audit export path separate approval | `SIEM_PUSH_ENABLED` staged rollout |

---

## Accepted risks

| ID | Risk | Rationale |
|----|------|-----------|
| A-1 | No SCIM delta **production scheduler** | By design until certification + architecture sign-off |
| A-2 | No **normal user JWT denylist** | Break-glass-only scope (Faz 122) |
| A-3 | Break-glass session limit in-memory per pod | Documented; low session cap (1) |
| A-4 | Frontend toolchain tests skipped on some dev machines | Backend review independent; CI runs frontend in `ci.yml` |
| A-5 | `adminGitopsProvider=mock` in chart default | Prevents accidental live PR until configured |

---

## Required staging evidence

1. **Retention:** `retention-staging-smoke-evidence` artifact (optional if datasource stays disabled in prod).
2. **SCIM:** `scim-delta-sandbox-evidence-{provider}` with `certificationResult: certified` or documented `needs-review`.
3. **Break-glass:** `break-glass-revocation-evidence` with `result: passed` and `gatewayRejectErrorCode: BREAK_GLASS_TOKEN_REVOKED`.

---

## Required production approval gates

| Flag group | Approvers | Evidence |
|------------|-----------|----------|
| Break-glass + denylist | Security + on-call lead | PP-2, runbook sign-off |
| SCIM delta remote fetch / multi-page | Identity + integration owner | PP-1, provider certification |
| Retention datasource | DBA + security | PP-3, RLS preflight SQL |
| Admin GitOps live provider | Platform + security | Mock → github config + branch rules |
| `ADMIN_RBAC_ENFORCE=true` | Security | Permission matrix review |

---

## Rollback readiness

| Surface | Rollback |
|---------|----------|
| Break-glass | Set `BREAK_GLASS_ENABLED=false`, `GATEWAY_BREAK_GLASS_ADMIN_ALLOWED=false`; revoke active sessions |
| Denylist | `GATEWAY_BREAK_GLASS_DENYLIST_CHECK_ENABLED=false` (emergency); DB denylist rows remain |
| SCIM delta | `SCIM_DELTA_*=false`; no scheduler to stop |
| Retention datasource | `retentionDatasource.*.enabled=false`; pods use primary pool |
| GitOps PR | `adminGitopsPrEnabled=false` |

---

## Final go / no-go recommendation

| Audience | Recommendation |
|----------|----------------|
| **Merge / ship backend artifacts** | **GO** — defaults safe, tests green, docs complete |
| **Enable high-risk flags in production** | **NO-GO** until PP-1..PP-3 addressed per flag family |
| **Overall backend readiness** | **READY_WITH_PREPROD_BLOCKERS** |

**Faz 125 — automated approval gate:** Build [`backend-preprod-evidence-bundle.json`](backend-preprod-evidence-bundle.md) from live staging artifacts via `scripts/security/build-backend-preprod-evidence-bundle.sh` or workflow `backend-preprod-evidence-bundle.yml` (`workflow_dispatch` only). Rules: [`backend-production-approval-gate.md`](backend-production-approval-gate.md). Missing live inputs → `finalRecommendation: NO_GO` (expected until staging jobs complete).

**Faz 126 — flag flip plan (no prod enables in repo):** After bundle `GO`, follow [`backend-production-flag-flip-plan.md`](backend-production-flag-flip-plan.md) wave order and [`backend-production-rollback-matrix.md`](backend-production-rollback-matrix.md). CR template: [`backend-production-change-request-template.md`](backend-production-change-request-template.md). CI guard: `scripts/security/validate-production-flag-plan.sh`.

**Faz 127 — RC readiness gate:** Single aggregator `scripts/security/ci-backend-rc-readiness.sh` + workflow `.github/workflows/backend-rc-readiness.yml`. Artifacts: `backend-rc-readiness-results.json`, `backend-rc-readiness-summary.md`. Verdicts: `PASS`, `PASS_WITH_ENVIRONMENT_SKIPS`, `FAIL`. Docker/Testcontainers not required for this gate.

**Faz 128 — Docker CI gate:** `scripts/security/ci-backend-docker-check.sh` + `.github/workflows/backend-docker-ci.yml`. Full `./gradlew check` + `rlsIntegrationTest` with Docker required on CI. Local: `BACKEND_DOCKER_ALLOW_SKIP=true` → `ENVIRONMENT_SKIPPED`. Artifacts: `backend-docker-ci-results.json`, `backend-docker-ci-summary.md`.

**Faz 129 — Docker CI green:** `:api-gateway:spotlessJavaCheck` fixed (import order + line wrap); identity `spotlessApply` + `ScimProperties` Spring record binding fix (`withLegacyDefaults` for tests). **Docker CI verdict: PASS** (full `check` + `rlsIntegrationTest`). Production flags unchanged.

**Faz 130 — staging PP execution plan (docs + input preparer):** Operator runbook [`backend-staging-pp-evidence-execution-plan.md`](backend-staging-pp-evidence-execution-plan.md) — PP-1 → PP-2 → PP-3 (if in prod scope) → bundle; CR attach list includes RC + Docker CI summaries. Helper: `scripts/security/prepare-backend-preprod-evidence-inputs.sh`. **Live staging PP evidence not run in review environment** — blockers remain operational until workflows execute with staging secrets.

**Faz 131 — RC sign-off package + freeze checklist:** [`backend-release-candidate-signoff.md`](backend-release-candidate-signoff.md) aggregates RC/Docker/PP verdicts and final **GO / NO_GO / GO_WITH_ACCEPTED_RISKS**; [`backend-release-freeze-checklist.md`](backend-release-freeze-checklist.md) gates post-freeze changes. Template: `scripts/security/generate-backend-rc-signoff-template.sh` (default **NO_GO**). **Production flags not enabled.**

**Faz 132 — live staging PP run:** [`backend-live-staging-pp-evidence-run-checklist.md`](backend-live-staging-pp-evidence-run-checklist.md) + `run-backend-live-staging-pp-evidence.sh`. **Live PP-1/PP-2 not executed** in review environment (**MISSING_SECRET**); bundle **NO_GO** until staging workflows run. **Production flags not enabled.**

**Faz 133 — secrets setup + dispatch:** [`backend-staging-pp-secrets-setup.md`](backend-staging-pp-secrets-setup.md); enhanced `check-staging-pp-secrets.sh`, `download-backend-pp-artifacts.sh`. **Workflow dispatch NOT_RUN** in review (secrets not in local env; use `--check-github` on operator machine). **Production flags not enabled.**

**Faz 134 — secrets governance + rotation:** [`backend-staging-pp-secrets-governance.md`](backend-staging-pp-secrets-governance.md); `check-staging-pp-secrets.sh --print-required`. Per-secret owner/TTL/rotate-after-run. **Live workflows still NOT_RUN** until secrets provisioned. **Production flags not enabled.**

**Faz 135 — orchestrator finalization:** `run-backend-live-staging-pp-evidence.sh` modes + `pp-input/` layout; download manifest + wait. **Live workflows NOT_RUN** in review. **Production flags not enabled.**

**Faz 136 — live PP evidence execution:** Operasyonel `--all` run (`rc-2026-05-17`, provider `okta`, `pp3_required=false`). GitHub probe: PP-1/PP-2 **MISSING_SECRET** → dispatch/download **NOT_RUN**; bundle **`finalRecommendation: NO_GO`** under `live-pp-evidence-out/`. **Production flags not enabled.**

**Faz 137 — secrets applied + bundle GO (attempt):** Probe enhanced (repo + environment `staging` secret names). **Secrets still not provisioned** on linked GitHub repo (`github_environment_staging: absent`); live workflows **NOT_RUN**; bundle **NO_GO**. **Not ready for backend production sign-off.** **Production flags not enabled.**

---

## Manual UI verification (reference)

See [`break-glass-revocation-staging-drill.md`](break-glass-revocation-staging-drill.md) § Manual UI checklist. Enterprise Security break-glass card and active sessions panel require `FRONTEND_BREAK_GLASS_REVOCATION_UI_ENABLED` on staging.

---

## Validation performed (Faz 124–136)

| Command | Result |
|---------|--------|
| `bash scripts/check-no-secrets.sh` | PASS |
| `bash scripts/security/ci-backend-production-readiness-review.sh` | PASS |
| `bash scripts/security/ci-backend-preprod-evidence-bundle.sh` | PASS (Faz 125 fixtures) |
| `bash scripts/security/ci-prepare-backend-preprod-evidence-inputs.sh` | PASS (Faz 130) |
| `bash scripts/security/ci-generate-backend-rc-signoff-template.sh` | PASS (Faz 131) |
| `bash scripts/security/ci-run-backend-live-staging-pp-evidence.sh` | PASS (Faz 132) |
| `bash scripts/security/ci-check-staging-pp-secrets.sh` | PASS (Faz 133) |
| `bash scripts/security/ci-download-backend-pp-artifacts.sh` | PASS (Faz 133) |
| `bash scripts/security/check-staging-pp-secrets.sh --print-required` | PASS (Faz 134) |
| `bash scripts/security/ci-check-staging-pp-secrets.sh` | PASS (Faz 134) |
| `bash scripts/security/ci-run-backend-live-staging-pp-evidence.sh` | PASS (Faz 135–136) |
| `bash scripts/security/ci-prepare-backend-preprod-evidence-inputs.sh` | PASS (Faz 135) |
| `bash scripts/security/run-backend-live-staging-pp-evidence.sh --all` (live) | NO_GO — secrets missing (Faz 136) |
| `bash scripts/security/check-staging-pp-secrets.sh --check-github --json` | MISSING_SECRET PP-1/PP-2 (Faz 136–137) |
| `bash scripts/security/run-backend-live-staging-pp-evidence.sh --all` (live) | NO_GO — secrets not provisioned (Faz 137) |
| `bash scripts/security/ci-check-staging-pp-secrets.sh` | PASS (Faz 137) |
| `bash scripts/security/ci-production-flag-plan.sh` | PASS (Faz 126) |
| `bash scripts/security/ci-backend-rc-readiness.sh` | PASS / PASS_WITH_ENVIRONMENT_SKIPS (Faz 127) |
| `bash scripts/security/ci-backend-docker-check.sh` | PASS (Faz 129–130) |
| `./gradlew :api-gateway:spotlessCheck` | PASS (Faz 129) |
| `bash scripts/retention/ci-retention-smoke-fixtures.sh` | PASS |
| `bash scripts/scim/ci-scim-delta-remote-fetch-fixtures.sh` | PASS |
| `bash scripts/security/ci-break-glass-revocation-fixtures.sh` | PASS |
| `./gradlew :identity-service:test --tests "*BreakGlass*" --tests "*Scim*" --tests "*Admin*"` | PASS |
| `./gradlew :api-gateway:test --tests "*BreakGlass*" --tests "*Scim*" --tests "*AdminPlatformRetention*"` | PASS |
| `bash scripts/helm-template-check.sh` | Not run locally (helm optional); CI runs on PR |
| Frontend vitest/tsc | SKIP (toolchain not on PATH in review environment) |
