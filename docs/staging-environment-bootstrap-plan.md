# Staging environment bootstrap plan (Faz 151)

Minimum **staging** infrastructure and operational sequence required before **live** PP-1 (SCIM delta sandbox) and PP-2 (break-glass revocation drill) evidence, backend pre-prod bundle **GO**, and platform sign-off **GO**.

**Does not deploy production.** **Does not enable production feature flags.** **Does not store secret values in this document.**

Related:

| Doc | Role |
|-----|------|
| [backend-staging-pp-secrets-setup.md](backend-staging-pp-secrets-setup.md) | GitHub secret names + workflow dispatch |
| [backend-staging-pp-secrets-governance.md](backend-staging-pp-secrets-governance.md) | Ownership, TTL, rotation |
| [backend-live-staging-pp-evidence-run-checklist.md](backend-live-staging-pp-evidence-run-checklist.md) | PP run after bootstrap |
| [platform-release-candidate-signoff.md](platform-release-candidate-signoff.md) | Platform decision rules |
| [deploy/gitops/environments/staging/](../deploy/gitops/environments/staging/) | Example overlays (no live creds) |
| [staging-deployment-smoke-runbook.md](staging-deployment-smoke-runbook.md) | RC deploy + smoke gate before PP (Faz 152) |

---

## Current release posture (Faz 151)

| Domain | Decision | Blocker |
|--------|----------|---------|
| Frontend | **GO_WITH_ACCEPTED_RISKS** | None for visual QA / RC gate |
| Backend | **NO_GO** | Staging not bootstrapped; PP-1/PP-2 live evidence missing |
| Platform | **NO_GO** | Backend **NO_GO** until PP bundle **GO** |

---

## 1. Minimum staging infrastructure

### 1.1 Network and ingress

| Requirement | Description | Example placeholder (replace per org) |
|-------------|-------------|----------------------------------------|
| Staging domain | DNS hostname for public API | `api.staging.example.com` |
| Public HTTPS gateway URL | Scheme + host, no path | `https://api.staging.example.com` |
| TLS | Valid cert (Let’s Encrypt or corp PKI) | Terminated at ingress / gateway |
| GitHub Actions egress | Runners can reach staging HTTPS | Allowlist staging ingress IP or VPN |

### 1.2 Compute and GitOps

| Component | Required | Notes |
|-----------|----------|--------|
| Kubernetes cluster (or equivalent) | Yes | Staging namespace / Argo CD app |
| Container registry | Yes | Images tagged for RC SHA (e.g. `staging`, `rc-*`) |
| GitOps / Helm | Yes | [deploy/gitops/environments/staging/](../deploy/gitops/environments/staging/) |
| Frontend URL | Recommended | Staging SPA URL or documented preview URL for manual smoke (not required for PP workflows) |

### 1.3 Backend services (minimum for PP evidence)

| Service | Required for | PP dependency |
|---------|--------------|---------------|
| **api-gateway** | All HTTP entry | PP-1, PP-2 |
| **identity-service** | Auth, admin tokens, break-glass, SCIM | PP-1, PP-2 |
| **workspace-service** | Platform health / admin paths | Smoke |
| **content-service** | Core platform | Smoke |
| **notification-service** | Platform health | Smoke (PP-3 if retention scope) |
| **search-service** | Platform health | Smoke (PP-3 if retention scope) |

### 1.4 Data and messaging

| Dependency | Required | Notes |
|------------|----------|--------|
| **PostgreSQL** | Yes | Per-service DBs or schemas; migrations applied |
| **Redis** | Yes | Sessions, denylist, gateway caches (break-glass revoke) |
| **Kafka** | As per chart | Required if notification/search/async paths enabled in staging values |

### 1.5 Staging-only configuration (flags — not production)

| Area | Staging expectation | Production |
|------|---------------------|------------|
| SCIM delta dry-run / remote fetch | Overlays per [scim-delta-remote-fetch-enable.overlay.example.yaml](../deploy/gitops/environments/staging/scim-delta-remote-fetch-enable.overlay.example.yaml) | **Not enabled** without CR |
| Break-glass drill | Staging drill flags per [break-glass-revocation-staging-drill.md](break-glass-revocation-staging-drill.md) | `GATEWAY_BREAK_GLASS_ADMIN_ALLOWED` stays false in prod unless CR |
| IdP SCIM bearer | ExternalSecret / Vault — [externalsecret-scim-delta-keys.example.yaml](../deploy/gitops/environments/staging/externalsecret-scim-delta-keys.example.yaml) | Never in GitHub |

---

## 2. Required service flows (PP evidence)

| Flow | Purpose | Evidence |
|------|---------|----------|
| Admin login / token | Platform admin JWT for diagnostics APIs | `SCIM_DELTA_SANDBOX_ADMIN_ACCESS_TOKEN`, `BREAK_GLASS_STAGING_ADMIN_ACCESS_TOKEN` |
| Break-glass issuance | Obtain drill JWT or use emergency path | `BREAK_GLASS_STAGING_BREAK_GLASS_TOKEN` **or** `BREAK_GLASS_STAGING_EMERGENCY_TOKEN` |
| Break-glass revoke + denylist | Admin revokes; gateway rejects token | PP-2: `result: passed`, `401`, `BREAK_GLASS_TOKEN_REVOKED` |
| SCIM delta sandbox | Remote fetch dry-run against sandbox IdP | PP-1: `certificationResult: certified` |
| GitHub runner → gateway | Workflows call staging HTTPS | PP-1/PP-2 job success |

No provider **mutation** in production. Staging drills are **read-only / revoke-only** per runbooks.

---

## 3. GitHub secrets mapping (names only)

Prefer **GitHub Environment `staging`**; workflows must use `environment: staging` or equivalent so secrets are visible to jobs.

| GitHub secret / variable | Maps to | How to obtain (no values in docs) |
|--------------------------|---------|-----------------------------------|
| `SCIM_DELTA_SANDBOX_GATEWAY_BASE_URL` | Staging gateway base URL | Staging DNS + ingress (§1.1) |
| `SCIM_DELTA_SANDBOX_ADMIN_ACCESS_TOKEN` | Platform admin JWT | Staging admin login / token broker (short TTL) |
| `SCIM_DELTA_SANDBOX_PROVIDER` (var) | `okta` / `entra` / `generic` | Runbook choice |
| `SCIM_DELTA_SANDBOX_EXPECT_READY` (var) | `false` until RC gate | Release manager |
| `BREAK_GLASS_STAGING_API_BASE_URL` | Same gateway URL as PP-1 | Staging gateway |
| `BREAK_GLASS_STAGING_ADMIN_ACCESS_TOKEN` | Admin JWT (MFA if required) | Staging admin auth flow |
| `BREAK_GLASS_STAGING_BREAK_GLASS_TOKEN` | Pre-issued break-glass JWT | Staging break-glass issuance flow |
| `BREAK_GLASS_STAGING_EMERGENCY_TOKEN` (alt) | Emergency login token | Staging break-glass static path — **one of** with row above |
| `BREAK_GLASS_STAGING_TEST_ENDPOINT` (var) | e.g. `/admin/enterprise/status` | Safe GET for post-revoke probe |
| `BREAK_GLASS_STAGING_IMAGE_TAG` (var) | Staging image tag | CI / GitOps |

**Cluster-only (not GitHub):** IdP sandbox bearer for SCIM remote fetch → Vault + ExternalSecret (see governance doc).

Probe (presence only):

```bash
bash scripts/security/check-staging-pp-secrets.sh --print-required
bash scripts/security/check-staging-pp-secrets.sh --check-github
```

---

## 4. Secret provenance (where each credential comes from)

| Credential role | Source system | Operator action |
|-----------------|---------------|-----------------|
| Gateway base URL | Staging ingress / DNS | Record HTTPS origin in GitHub secret |
| SCIM admin JWT | Identity-service staging admin user | Login or broker; paste via `gh secret set … --body-file -` |
| Break-glass admin JWT | Same or dedicated break-glass admin | Short-lived; MFA per policy |
| Break-glass drill JWT | Staging break-glass issuance API/UI | Issue for drill `jti`; revoke after drill |
| Emergency token (optional) | Staging emergency path | Only if org uses static emergency instead of JWT |
| SCIM IdP bearer | Sandbox Okta/Entra app | Vault secret referenced by ExternalSecret |

**Forbidden:** Committing values; attaching tokens to sign-off markdown; logging full JWT in CI output (workflows must sanitize).

---

## 5. Staging bring-up sequence

Execute in order. Do not run PP-1/PP-2 until step 15 prerequisites pass.

| Step | Phase | Action | Exit criteria |
|------|-------|--------|---------------|
| 1 | Infra | Provision cluster, namespace, Argo CD | Cluster API healthy |
| 2 | DNS + TLS | Point staging domain to ingress; cert valid | `curl -sI https://<gateway>/actuator/health` or platform health path |
| 3 | Data | PostgreSQL + Redis (+ Kafka if required) | Services connect; migrations OK |
| 4 | Deploy | Backend services + gateway (RC image tag) — see [staging-deployment-smoke-runbook.md](staging-deployment-smoke-runbook.md) §2 | All pods ready; no crash loop |
| 5 | Gateway | Verify routing to identity, admin APIs | Health 200 |
| 6 | Frontend | Deploy staging SPA or document preview URL | Optional manual smoke |
| 7 | Admin bootstrap | Create platform admin user(s) in staging IdP/DB | Admin can log in to staging UI |
| 8 | Admin token | Obtain admin JWT for PP secrets | Token works on admin GET (smoke) |
| 9 | Break-glass | Enable staging drill flags (not prod) | Issuance path available |
| 10 | Break-glass token | Obtain drill JWT or emergency credential | Token authenticates once |
| 11 | SCIM sandbox | Configure sandbox IdP + ExternalSecret bearer | Remote fetch dry-run enabled in staging only |
| 12 | GitHub | Create Environment `staging` + set secrets (§3) | `check-staging-pp-secrets.sh --check-github` all **present** |
| 13 | PP-1 | `gh workflow run scim-delta-readiness.yml` | Artifact: `certificationResult: certified` |
| 14 | PP-2 | `gh workflow run break-glass-revocation-readiness.yml` | Artifact: `passed`, `401`, `BREAK_GLASS_TOKEN_REVOKED` |
| 15 | Bundle | `run-backend-live-staging-pp-evidence.sh --build-bundle` or CI bundle job | `finalRecommendation: GO` |
| 16 | Sign-off | Update backend + platform sign-off docs | Backend **GO** only after 15 |

---

## 6. Smoke checklist (post bring-up, pre PP dispatch)

Detailed deploy commands and SM-1–SM-9 procedures: [staging-deployment-smoke-runbook.md](staging-deployment-smoke-runbook.md) §3.

| # | Check | Pass criteria |
|---|-------|----------------|
| S1 | Gateway health | HTTPS 200 on health/actuator |
| S2 | Identity login | Admin can authenticate (UI or token endpoint) |
| S3 | Admin permission | Admin route returns 200 (not 403) for platform admin |
| S4 | Break-glass session list | Admin can list/revoke without raw token in UI |
| S5 | Break-glass revoke reject | After revoke: probe returns **401** + code **BREAK_GLASS_TOKEN_REVOKED** |
| S6 | SCIM delta readiness | Workflow or manual check: **certificationResult=certified** |
| S7 | No secret leakage | Logs/artifacts: no raw JWT, JDBC, IdP bearer in clear text |

---

## 7. Do not run when staging is absent

| Action | Reason |
|--------|--------|
| PP-1 `scim-delta-readiness.yml` (live) | Missing gateway / IdP / secrets → false evidence |
| PP-2 `break-glass-revocation-readiness.yml` (live) | Missing tokens → drill invalid |
| Backend pre-prod bundle **GO** | Fixture-only bundle is **NO_GO** for production sign-off |
| Backend domain sign-off **GO** | Requires live PP-1 + PP-2 on RC SHA |
| Platform sign-off **GO** | Requires backend **GO** + frontend sign-off |
| Production flag wave CR | Blocked by [backend-production-flag-flip-plan.md](backend-production-flag-flip-plan.md) until platform **GO** |

---

## 8. PP-1 / PP-2 live evidence conditions

| PP | Pass condition | Artifact |
|----|----------------|----------|
| PP-1 | `certificationResult: certified` | `scim-delta-sandbox-evidence.json` |
| PP-2 | `result: passed`, gateway reject `401`, `BREAK_GLASS_TOKEN_REVOKED` | `break-glass-revocation-evidence.json` |
| PP-3 | `not_required` **or** all retention domains `passed` | Only if prod CR requires dedicated retention datasource |

Bundle aggregate: [backend-preprod-evidence-bundle.md](backend-preprod-evidence-bundle.md) — `finalRecommendation: GO` required for backend sign-off **GO**.

---

## 9. Platform sign-off linkage

| Until staging bootstrap complete | Decision |
|----------------------------------|----------|
| Frontend | **GO_WITH_ACCEPTED_RISKS** (unchanged) |
| Backend | **NO_GO** |
| Platform | **NO_GO** |

After steps 1–16 succeed on RC SHA: update [platform-release-candidate-signoff.md](platform-release-candidate-signoff.md) and [backend-release-candidate-signoff.md](backend-release-candidate-signoff.md) — still **no production flags**.

---

## 10. Operator quick reference

```bash
# Catalog
bash scripts/security/check-staging-pp-secrets.sh --print-required

# GitHub presence (no values)
bash scripts/security/check-staging-pp-secrets.sh --check-github

# Full pipeline (after secrets + staging healthy)
bash scripts/security/run-backend-live-staging-pp-evidence.sh --all --rc-id <RC_ID> --provider okta --pp3-required false
```
