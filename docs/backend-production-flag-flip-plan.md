# Backend production flag flip plan (Faz 126)

Operational sequence for enabling high-risk backend flags **after** the pre-prod evidence bundle returns **`GO`** or signed **`GO_WITH_ACCEPTED_RISKS`**. This document does **not** authorize any change by itself; each wave requires a GitOps CR using [`backend-production-change-request-template.md`](backend-production-change-request-template.md).

| Related | |
|---------|---|
| Evidence gate | [`backend-production-approval-gate.md`](backend-production-approval-gate.md) |
| Rollback steps | [`backend-production-rollback-matrix.md`](backend-production-rollback-matrix.md) |
| Chart keys | `deploy/helm/notebook-platform/values.yaml` |
| Prod overlay | `deploy/gitops/environments/prod/values.yaml` |

**Current repo state:** All listed flags remain **disabled/false** in chart and prod overlay. Validator: `scripts/security/validate-production-flag-plan.sh`.

---

## Prerequisites (Wave 0)

| Step | Action | Owner |
|------|--------|-------|
| 0.1 | Staging PP-1..PP-3 workflows complete; artifacts attached | Integration / Security |
| 0.2 | Build `backend-preprod-evidence-bundle`; `finalRecommendation` ∈ {`GO`, `GO_WITH_ACCEPTED_RISKS`} | Release manager |
| 0.3 | Faz 124 dangerous-defaults CI green on release branch | Platform |
| 0.4 | Argo CD sync healthy; no in-flight prod hotfix | SRE |
| 0.5 | Open CR from template; approvers assigned per flag family | Change author |

**No production GitOps merge** until 0.2 satisfied.

---

## Recommended opening sequence

| Wave | Purpose | Staging order | Production order |
|------|---------|---------------|------------------|
| **0** | Evidence + CR | — | — |
| **1** | Read-only diagnostics | Staging overlays first | `platformRetentionGovernanceEnabled`, SCIM POC read-only |
| **2** | Limited admin visibility | RBAC visibility observe | `adminRbacVisibilityEnabled`, enterprise status (existing) |
| **3** | Break-glass read + revocation (no admin write) | BG + revocation drill | `BREAK_GLASS_ENABLED` → `BREAK_GLASS_REVOCATION_ENABLED` |
| **4** | Gateway denylist guard | After PP-2 pass | `GATEWAY_BREAK_GLASS_DENYLIST_CHECK_ENABLED` |
| **5** | SCIM remote fetch POC (dry-run, no scheduler) | Per-provider cert | `SCIM_DELTA_PROVIDER_POC_ENABLED` → remote fetch → multi-page last |
| **6** | Retention dry-run + integration (no purge) | Per-domain smoke | Service dry-run → gateway integration per domain |
| **7** | Retention dedicated datasource | DBA E2E | `retentionDatasource.<svc>.enabled` one service at a time |
| **8** | Admin GitOps PR automation | Mock → github staging | `adminGitopsPrEnabled` + live provider |
| **9** | RBAC enforce / overrides | Staging enforce pilot | `gatewayAdminRbacEnforce`, overrides reload (optional) |

**Never in production:** `GATEWAY_BREAK_GLASS_ADMIN_ALLOWED=true`, `BREAK_GLASS_ALLOW_ADMIN_WRITE=true`, `scimDeltaSyncEnabled=true`, destructive purge UI, auto-merge.

---

## Flag catalog

Helm `config.*` keys map to pod env vars via `deploy/helm/notebook-platform/templates/configmap.yaml` (e.g. `breakGlassEnabled` → `BREAK_GLASS_ENABLED`).

### Group A — Break-glass revocation / denylist

| Helm key | Env var | Default | Prod target (when approved) | Prerequisite evidence | Approvers | Blast radius | Monitoring | Rollback | Owner | Canary order |
|----------|---------|---------|----------------------------|------------------------|-----------|--------------|------------|----------|-------|--------------|
| `breakGlassEnabled` | `BREAK_GLASS_ENABLED` | `false` | `true` (narrow modes only) | PP-2 pass; [break-glass-runbook](break-glass-runbook.md) | Security + on-call | Emergency access path live | `break_glass_session_*`, audit events | Set `false`; revoke sessions | Security | Staging → prod wave 3 |
| `breakGlassRevocationEnabled` | `BREAK_GLASS_REVOCATION_ENABLED` | `false` | `true` | PP-2; denylist table migrated | Security | Revoke API + DB denylist | `break_glass_revocation_*` | Set `false` | Security | After `breakGlassEnabled` |
| `gatewayBreakGlassDenylistCheckEnabled` | `GATEWAY_BREAK_GLASS_DENYLIST_CHECK_ENABLED` | `false` | `true` | PP-2 gateway `BREAK_GLASS_TOKEN_REVOKED` | Security + SRE | All admin routes via gateway | `gateway_break_glass_denylist_*` | Set `false`; wait cache TTL | SRE | After revocation enabled |
| `gatewayBreakGlassDenylistCacheSeconds` | `GATEWAY_BREAK_GLASS_DENYLIST_CACHE_SECONDS` | `30` | `30` (tune later) | Drill documents TTL window | Security | Brief post-revoke acceptance | Denylist reject rate | Reduce TTL or disable check | SRE | With wave 4 |
| `gatewayBreakGlassAdminAllowed` | `GATEWAY_BREAK_GLASS_ADMIN_ALLOWED` | `false` | **`false` (forbidden)** | N/A — not in rollout | — | Break-glass JWT on admin writes | — | Keep `false` | Security | **Do not enable** |
| `breakGlassAllowAdminWrite` | `BREAK_GLASS_ALLOW_ADMIN_WRITE` | `false` | **`false` (forbidden)** | N/A | — | Identity admin-write bypass | — | Keep `false` | Security | **Do not enable** |

### Group B — SCIM delta POC / remote fetch

| Helm key | Env var | Default | Prod target | Prerequisite | Approvers | Blast radius | Monitoring | Rollback | Owner | Canary order |
|----------|---------|---------|-------------|--------------|-----------|--------------|------------|----------|-------|--------------|
| `scimDeltaProviderPocEnabled` | `SCIM_DELTA_PROVIDER_POC_ENABLED` | `false` | `true` | PP-1 certified; dry-run only | Identity + integration | Diagnostics endpoints | SCIM delta metrics | `false` | Identity | Wave 5a staging → prod |
| `scimDeltaDryRunOnly` | `SCIM_DELTA_DRY_RUN_ONLY` | `true` | **`true` (mandatory)** | Certification | Identity | No writes from delta | Audit dry-run events | Keep `true` | Identity | Always on |
| `scimDeltaRemoteFetchEnabled` | `SCIM_DELTA_REMOTE_FETCH_ENABLED` | `false` | `true` (per provider CR) | PP-1; ExternalSecret wired | Identity + Security | Outbound IdP API calls | Fetch latency/errors | `false` | Identity | After POC; single-page first |
| `scimDeltaRemoteMultiPageEnabled` | `SCIM_DELTA_REMOTE_MULTI_PAGE_ENABLED` | `false` | `true` (optional) | Single-page stable 7d | Identity | Multi-page volume | Page/resource caps | `false` | Identity | After single-page |
| `scimDeltaRemoteFetch.bearerTokenFromSecret.enabled` | `SCIM_DELTA_REMOTE_BEARER_TOKEN` (secret ref) | `false` | `true` with `existingSecret` | Secret in vault; no token in Git | Security | Bearer exfil if misconfigured | Secret mount failures | Disable fetch + secret | Security | Same CR as remote fetch |
| `scimDeltaSyncEnabled` | `SCIM_DELTA_SYNC_ENABLED` | `false` | **`false` (forbidden)** | Architecture sign-off not granted | — | Scheduled sync / deprovision risk | — | Keep `false` | Identity | **Never in this plan** |

See [`scim-delta-provider-certification.md`](scim-delta-provider-certification.md), [`scim-delta-sandbox-evidence.md`](scim-delta-sandbox-evidence.md).

### Group C — Retention governance / datasource / dry-run

| Helm key | Env var | Default | Prod target | Prerequisite | Approvers | Blast radius | Monitoring | Rollback | Owner | Canary order |
|----------|---------|---------|-------------|--------------|-----------|--------------|------------|----------|-------|--------------|
| `platformRetentionGovernanceEnabled` | `PLATFORM_RETENTION_GOVERNANCE_ENABLED` | `false` | `true` | Legal/compliance sign-off | Security + legal | Platform plan aggregation | `platform_retention_*` | `false` | Platform | Wave 1 |
| `contentRetentionDryRunEnabled` | `CONTENT_RETENTION_DRY_RUN_ENABLED` | `false` | `true` | RLS runbook; PP-3 if datasource scope | DBA + Security | COUNT queries on content DB | Dry-run warnings | `false` | Content | Wave 6 per domain |
| `contentRetentionIntegrationEnabled` | `CONTENT_RETENTION_INTEGRATION_ENABLED` | `false` | `true` | Dry-run stable | DBA + Security | Gateway merges content plan | Enterprise status partial | `false` | Gateway/SRE | After content dry-run |
| `notificationRetentionDryRunCountsEnabled` | `NOTIFICATION_RETENTION_DRY_RUN_COUNTS_ENABLED` | `false` | `true` | Notification RLS runbook | DBA + Security | COUNT on notification DB | Same | `false` | Notification | Wave 6 |
| `notificationRetentionIntegrationEnabled` | `NOTIFICATION_RETENTION_INTEGRATION_ENABLED` | `false` | `true` | Dry-run pass | DBA + Security | Gateway merge | Same | `false` | Gateway | After notification dry-run |
| `workspaceRetentionDryRunCountsEnabled` | `WORKSPACE_RETENTION_DRY_RUN_COUNTS_ENABLED` | `false` | `true` | Workspace RLS runbook | DBA + Security | COUNT workspace | Same | `false` | Workspace | Wave 6 |
| `workspaceRetentionIntegrationEnabled` | `WORKSPACE_RETENTION_INTEGRATION_ENABLED` | `false` | `true` | Dry-run pass | DBA + Security | Gateway merge | Same | `false` | Gateway | After workspace dry-run |
| `searchRetentionDryRunCountsEnabled` | `SEARCH_RETENTION_DRY_RUN_COUNTS_ENABLED` | `false` | `true` | Search RLS runbook | DBA + Security | COUNT search | Same | `false` | Search | Wave 6 |
| `searchRetentionIntegrationEnabled` | `SEARCH_RETENTION_INTEGRATION_ENABLED` | `false` | `true` | Dry-run pass | DBA + Security | Gateway merge | Same | `false` | Gateway | After search dry-run |
| `retentionDatasource.<svc>.enabled` | (datasource binding) | `false` | `true` per service | PP-3; DBA E2E; ExternalSecret | DBA + Security | Separate JDBC pool | Pool health / errors | `false`; pod restart | DBA | Wave 7 one svc/CR |
| `notificationRetentionWorkerEnabled` | (notification worker) | `false` | **`false` until purge approved** | Destructive purge out of scope | — | Deletes | — | Keep `false` | — | **Not in wave 6** |
| `notificationRetentionDryRunOnly` | — | `true` | **`true` if worker ever enabled** | — | — | — | — | — | — | Mandatory guard |
| Frontend `FRONTEND_NOTIFICATION_RETENTION_PURGE_ENABLED` | — | `false` | **`false` (forbidden)** | Legal + purge runbook | — | User-triggered purge | — | Keep `false` | — | **Never** |

See [`platform-retention-governance.md`](platform-retention-governance.md), [`retention-datasource-ops-handoff.md`](retention-datasource-ops-handoff.md).

### Group D — Admin GitOps PR automation

| Helm key | Env var | Default | Prod target | Prerequisite | Approvers | Blast radius | Monitoring | Rollback | Owner | Canary order |
|----------|---------|---------|-------------|--------------|-----------|--------------|------------|----------|-------|--------------|
| `adminGitopsPrEnabled` | `ADMIN_GITOPS_PR_ENABLED` | `false` | `true` | Approved change-request flow tested | Platform + Security | PRs to infra repo | GitOps PR metrics | `false` | Platform | Wave 8 |
| `adminGitopsProvider` | `ADMIN_GITOPS_PROVIDER` | `mock` | `github` (example) | GitHub App + branch protection | Platform + Security | Live VCS writes | PR failure alerts | Revert to `mock` | Platform | Same CR |
| `adminGitopsRequireApprovedChange` | `ADMIN_GITOPS_REQUIRE_APPROVED_CHANGE` | `true` | `true` | — | Security | Blocks unapproved PRs | — | Keep `true` | Security | Always |
| Auto-merge | — | — | **disabled (forbidden)** | [`admin-gitops-pr-automation.md`](admin-gitops-pr-automation.md) | — | — | — | — | — | **Never** |

### Group E — RBAC runtime overrides (optional wave)

| Helm key | Env var | Default | Prod target | Prerequisite | Approvers | Blast radius | Monitoring | Rollback | Owner | Canary order |
|----------|---------|---------|-------------|--------------|-----------|--------------|------------|----------|-------|--------------|
| `adminRbacEnabled` | `ADMIN_RBAC_ENABLED` | `false` | `true` | Permission matrix | Security | Admin route authz | 403 rate | `false` | Security | Wave 9 |
| `gatewayAdminRbacEnforce` | `GATEWAY_ADMIN_RBAC_ENFORCE` | `false` | `true` | Staging enforce pilot | Security | Gateway 403/401 | RBAC deny metrics | `false` | SRE | After identity RBAC |
| `adminRbacOverridesEnabled` | — | `false` | `true` (optional) | Mounted overrides file + checksum | Security | Effective permissions | Override reload errors | Disable reload | Security | Last |
| `adminRbacOverridesReloadEnabled` | — | `false` | `true` only with watch off-hours | Staging reload test | Security | Hot reload | — | `false` | Security | Optional |

PP-3 not required if `retentionDatasource` stays disabled; set `PREPROD_PP3_REQUIRED` when wave 7 is in scope.

---

## Forbidden combinations

| ID | Combination | Reason |
|----|-------------|--------|
| F-1 | `scimDeltaSyncEnabled=true` | No production scheduler / sync job in this release |
| F-2 | `breakGlassAllowAdminWrite=true` OR `gatewayBreakGlassAdminAllowed=true` | No break-glass admin write unblock |
| F-3 | `scimDeltaRemoteFetchEnabled=true` AND `scimDeltaDryRunOnly=false` | Remote fetch must stay dry-run |
| F-4 | `retentionDatasource.*.enabled=true` without PP-3 pass | No dedicated DB without E2E evidence |
| F-5 | `*_INTEGRATION_ENABLED=true` without domain dry-run stable | No gateway merge before service counts |
| F-6 | `FRONTEND_*_PURGE_ENABLED=true` or worker enabled without `dryRunOnly=true` | No destructive purge |
| F-7 | GitOps auto-merge on admin PRs | Manual review required |
| F-8 | `adminGitopsPrEnabled=true` + production flip without `adminGitopsRequireApprovedChange=true` | Unapproved changes |
| F-9 | Multiple wave families in one CR without combined approval | Blast radius control |

Enforced in CI: `scripts/security/validate-production-flag-plan.sh`.

---

## Evidence prerequisite mapping

| Wave / family | Bundle field | Upstream artifact |
|---------------|--------------|-------------------|
| Break-glass + denylist | `pp2BreakGlass.status=pass` | `break-glass-revocation-evidence.json` |
| SCIM remote fetch / multi-page | `pp1Scim.status=pass` | `scim-delta-sandbox-evidence.json` |
| Retention datasource | `pp3Retention.status=pass` | `retention-staging-smoke-results.json` |
| Retention dry-run only | PP-3 optional; domain runbooks | Staging smoke per service |
| GitOps live provider | Separate platform checklist | Approved change-request sample |
| RBAC enforce | Security matrix review | Not in PP bundle |

---

## GitOps change process

1. Branch from `main`; edit **only** `deploy/gitops/environments/prod/values.yaml` (or approved overlay path).
2. One wave per PR unless explicitly combined in CR.
3. Argo CD sync → wait healthy → run post-flip smoke (see rollback matrix).
4. Attach bundle + CR template to release ticket.

**This repository phase does not commit prod enable values.**

---

## Post-flip validation (per wave)

| Check | Command / endpoint |
|-------|-------------------|
| Bundle still valid | Re-run builder if artifacts refreshed |
| Chart guardrails | `bash scripts/security/validate-production-flag-plan.sh` |
| Break-glass | Drill script read-only or metric spot-check |
| SCIM | Admin diagnostics dry-run; no `certificationResult` regression |
| Retention | `scripts/retention/*-dry-run-smoke.sh` with prod gateway (read-only) |
| Gateway enterprise | `GET /admin/enterprise/status` — no secret fields |

---

## Threat model cross-reference

See [`security-threat-model.md`](security-threat-model.md) — break-glass, SCIM outbound, retention COUNT, GitOps PR surfaces.
