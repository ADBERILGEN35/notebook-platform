# Staging deployment + smoke runbook (Faz 152)

Operator runbook for deploying **RC images** to staging via **GitOps/Helm**, running **post-deploy smoke**, then handing off to **PP-1 / PP-2** live evidence workflows.

**Does not deploy production.** **Does not enable production feature flags.** **No secret values in this document.**

Related:

| Doc | Role |
|-----|------|
| [staging-environment-bootstrap-plan.md](staging-environment-bootstrap-plan.md) | Infra prerequisites (Faz 151) |
| [backend-live-staging-pp-evidence-run-checklist.md](backend-live-staging-pp-evidence-run-checklist.md) | PP dispatch after smoke |
| [backend-staging-pp-secrets-setup.md](backend-staging-pp-secrets-setup.md) | GitHub Environment `staging` secrets |
| [break-glass-revocation-staging-drill.md](break-glass-revocation-staging-drill.md) | PP-2 drill flags |
| [kubernetes-deployment.md](kubernetes-deployment.md) | Helm chart scope |
| [deploy/gitops/README.md](../deploy/gitops/README.md) | Argo CD layout |

Example overlay: [staging-rc-deploy.overlay.example.yaml](../deploy/gitops/environments/staging/staging-rc-deploy.overlay.example.yaml)

---

## Current release posture (Faz 152)

| Domain | Decision | Notes |
|--------|----------|-------|
| Frontend | **GO_WITH_ACCEPTED_RISKS** | Unchanged; not upgraded to full **GO** |
| Backend | **NO_GO** | Staging deploy + smoke + PP live evidence not executed on RC SHA |
| Platform | **NO_GO** | Backend **NO_GO** |
| Production flags | **Not enabled** | |

---

## 1. Staging GitOps / Helm inputs (documented values)

Source of truth for staging defaults: [deploy/gitops/environments/staging/values.yaml](../deploy/gitops/environments/staging/values.yaml).

RC promotion overlay (placeholders only): [staging-rc-deploy.overlay.example.yaml](../deploy/gitops/environments/staging/staging-rc-deploy.overlay.example.yaml).

| Value area | GitOps key / file | Operator sets | Example placeholder (replace per org) |
|------------|-------------------|---------------|-------------------------------------|
| RC image tag | `services.*.image.tag`, `frontend.image.tag` | Immutable tag from RC build (commit SHA or `rc-*`) | `staging-placeholder` → `<RC_IMAGE_TAG>` |
| Image registry | `global.imageRegistry` | Org container registry | `registry.example.com` |
| Gateway host | `ingress.hosts[].host` | Staging API DNS | `api.staging.example.com` |
| Frontend host | `frontend.ingress.hosts[].host` | Staging SPA DNS | `app.staging.example.com` |
| Public gateway URL | Derived | `https://` + gateway host | `https://api.staging.example.com` |
| CORS | `config.corsAllowedOrigins` | Frontend origin | `https://app.staging.example.com` |
| Frontend API | `frontend.env.FRONTEND_API_BASE_URL` | Same as gateway URL | `https://api.staging.example.com` |
| TLS | `ingress.tls`, `frontend.ingress.tls` | cert-manager or corp PKI secret names | `api-staging-example-com-tls` |
| PostgreSQL JDBC | `externalDatabase.*Url` | Managed PG host/db (no password in values) | `jdbc:postgresql://<host>:5432/<db>` |
| DB passwords | ExternalSecret → `notebook-platform-secrets` | Secret manager keys | `identity-db-password`, etc. |
| Redis | `externalRedis.host` + secret `redis-password` | Managed Redis | `redis.staging.example.internal` |
| Kafka | N/A in chart | **Not required** — workers use in-service outbox (see [kubernetes-deployment.md](kubernetes-deployment.md)) | — |
| ExternalSecret store | `secrets.externalSecrets.secretStoreRef` | ClusterSecretStore name | `notebook-platform-staging-secret-store` |
| Target K8s secret | `secrets.externalSecrets.targetSecretName` | Synced secret | `notebook-platform-secrets` |
| SCIM delta bearer | `scimDeltaRemoteFetch` + overlay | Vault path in [externalsecret-scim-delta-keys.example.yaml](../deploy/gitops/environments/staging/externalsecret-scim-delta-keys.example.yaml) | Cluster only |
| Admin RBAC overrides | `admin-rbac-overrides.yaml` | ConfigMap mount | Staging file in gitops env |
| Break-glass drill | `config.breakGlass*` overlay | Enable only for PP-2 window | See §5.4 |

Argo CD Application example: [deploy/gitops/argocd/app-staging.yaml](../deploy/gitops/argocd/app-staging.yaml) (`notebook-staging` namespace).

---

## 2. Staging deployment flow

### 2.1 Preconditions

| # | Check |
|---|--------|
| P1 | [staging-environment-bootstrap-plan.md](staging-environment-bootstrap-plan.md) steps 1–3 (cluster, DNS/TLS, PostgreSQL + Redis) |
| P2 | Backend RC readiness **PASS** and Docker CI **PASS** on target RC SHA |
| P3 | Frontend RC gate **PASS** on same SHA (for coordinated release line) |
| P4 | ExternalSecret / ClusterSecretStore configured; no plaintext secrets in git |
| P5 | `bash scripts/check-no-secrets.sh` **PASS** |

### 2.2 RC image acquisition

| Step | Action | Artifact |
|------|--------|----------|
| 1 | Run **Release Images Draft** (`.github/workflows/release-images.yml`) with `image_tag=<RC_SHA>`, `push_images=true` when registry ready | Images in registry |
| 2 | Record immutable tag (commit SHA) for sign-off attachment | RC manifest / workflow run URL |

Optional: use **GitOps Promote Draft** (`.github/workflows/gitops-promote.yml`) with `environment=staging`, `image_tag=<RC_SHA>`, `dry_run=true` first to preview `values.yaml` diff.

### 2.3 Promote tags to GitOps

| Step | Action |
|------|--------|
| 1 | Copy [staging-rc-deploy.overlay.example.yaml](../deploy/gitops/environments/staging/staging-rc-deploy.overlay.example.yaml) into private ops merge or update `values.yaml` tags via promote workflow |
| 2 | Set `<RC_IMAGE_TAG>` on all backend services + frontend |
| 3 | Set JDBC/Redis hosts and ingress hosts (placeholders only in repo examples) |
| 4 | Open PR; run `bash scripts/validate-yaml.sh` and `bash scripts/helm-template-check.sh` |
| 5 | Merge; sync Argo CD **notebook-platform-staging** (or `helm upgrade` equivalent) |

### 2.4 Helm render (local validation)

```bash
CHART=deploy/helm/notebook-platform
helm lint "$CHART"
helm template notebook-platform "$CHART" \
  -f deploy/gitops/environments/staging/values.yaml \
  -f deploy/gitops/environments/staging/staging-rc-deploy.overlay.example.yaml \
  > /tmp/notebook-staging-rc.yaml
```

When Helm is absent, `bash scripts/helm-template-check.sh` skips with exit 0 (CI still runs chart checks where Helm is installed).

### 2.5 Cluster rollout verification

Set `NS=notebook-staging` (or your namespace).

```bash
kubectl -n "$NS" rollout status deployment/notebook-platform-api-gateway --timeout=300s
kubectl -n "$NS" rollout status deployment/notebook-platform-identity --timeout=300s
kubectl -n "$NS" rollout status deployment/notebook-platform-workspace --timeout=300s
kubectl -n "$NS" rollout status deployment/notebook-platform-content --timeout=300s
kubectl -n "$NS" rollout status deployment/notebook-platform-notification --timeout=300s
kubectl -n "$NS" rollout status deployment/notebook-platform-search --timeout=300s
kubectl -n "$NS" rollout status deployment/notebook-platform-frontend --timeout=300s

kubectl -n "$NS" get pods -l app.kubernetes.io/instance=notebook-platform
kubectl -n "$NS" get externalsecret,secret | grep notebook-platform
```

Deployment names may differ if `fullnameOverride` is set — adjust from `helm template` output.

### 2.6 Gateway / identity health (HTTPS)

Set `GATEWAY_BASE=https://<STAGING_API_HOST>` (no trailing slash).

```bash
curl -sS -f "$GATEWAY_BASE/actuator/health" | head -c 500
curl -sS -f "$GATEWAY_BASE/actuator/health/readiness" >/dev/null

# Identity via gateway route or port-forward to identity service:
curl -sS -f "$GATEWAY_BASE/actuator/health"  # gateway aggregates routes; for identity-specific:
# kubectl -n "$NS" port-forward svc/notebook-platform-identity 8081:8081
# curl -sS -f http://127.0.0.1:8081/actuator/health
```

Frontend (optional):

```bash
FRONTEND_BASE=https://<STAGING_APP_HOST>
curl -sS -f "$FRONTEND_BASE/healthz" >/dev/null
```

Do **not** paste admin JWTs or break-glass tokens into shell history shared in tickets — use env files with restrictive permissions.

---

## 3. Staging smoke checklist (gate before PP)

Complete **all** rows before `gh workflow run` for PP-1 or PP-2.

| ID | Check | Procedure | Pass criteria |
|----|-------|-----------|---------------|
| SM-1 | Gateway health | §2.6 `curl` | HTTP 200; status UP |
| SM-2 | Identity login reachable | UI login or `POST /auth/login` with staging test user | 200; no 5xx |
| SM-3 | Admin bootstrap | Staging platform admin exists (`gatewayAdminAllowedEmails`) | Admin can authenticate |
| SM-4 | Admin token obtain | Org token broker / admin session → short-lived JWT for GitHub secrets | Admin GET returns 200 (not 403) |
| SM-5 | Admin permission | `GET` safe admin route (e.g. enterprise status) with admin JWT | 200 |
| SM-6 | Break-glass status | With drill flags enabled: list sessions API or Security UI | Endpoint reachable; no token column in UI |
| SM-7 | SCIM delta readiness | `GET /admin/identity/scim/delta/readiness` with admin JWT **or** await PP-1 artifact | `certificationResult` = **certified** (PP-1 is authoritative for evidence) |
| SM-8 | Break-glass revoke probe | Manual revoke + `GET` probe with revoked JWT | **401** + `BREAK_GLASS_TOKEN_REVOKED` (PP-2 is authoritative for evidence) |
| SM-9 | No secret logs | Scan pod logs / CI output for this session | No raw JWT, JDBC URL with password, IdP bearer |

Optional full API smoke (non-admin): `BASE_URL=$GATEWAY_BASE bash scripts/smoke-test.sh` (creates ephemeral user — staging only).

Record results in operator notes; attach to change request when promoting backend sign-off.

---

## 4. Admin / break-glass / SCIM procedures (no values)

### 4.1 Admin user bootstrap

1. Ensure staging IdP or local identity allows platform admin email in `gatewayAdminAllowedEmails`.
2. Apply [admin-rbac-overrides.yaml](../deploy/gitops/environments/staging/admin-rbac-overrides.yaml) via chart mount if overrides required.
3. Confirm admin login via staging frontend (`FRONTEND_ADMIN_UI_ENABLED=true` in staging values).

### 4.2 Admin access token (GitHub secrets)

1. Authenticate as platform admin on staging.
2. Obtain short-lived admin JWT per org runbook (browser devtools forbidden for CR attachments — use approved broker).
3. Set GitHub Environment **staging** secrets per [backend-staging-pp-secrets-setup.md](backend-staging-pp-secrets-setup.md):
   - `SCIM_DELTA_SANDBOX_ADMIN_ACCESS_TOKEN`
   - `BREAK_GLASS_STAGING_ADMIN_ACCESS_TOKEN`
4. Verify: `bash scripts/security/check-staging-pp-secrets.sh --check-github` → **present** (names only).

### 4.3 Break-glass token obtain

1. Merge break-glass drill flags (see [break-glass-revocation-staging-drill.md](break-glass-revocation-staging-drill.md)); sync deployment.
2. Issue drill JWT or configure `BREAK_GLASS_STAGING_EMERGENCY_TOKEN` path (one required).
3. Set `BREAK_GLASS_STAGING_BREAK_GLASS_TOKEN` or emergency secret in GitHub **staging** environment.
4. Set `BREAK_GLASS_STAGING_API_BASE_URL` to gateway base URL.

### 4.4 SCIM sandbox

1. Sync IdP sandbox bearer via ExternalSecret ([externalsecret-scim-delta-keys.example.yaml](../deploy/gitops/environments/staging/externalsecret-scim-delta-keys.example.yaml)).
2. Merge [scim-delta-remote-fetch-enable.overlay.example.yaml](../deploy/gitops/environments/staging/scim-delta-remote-fetch-enable.overlay.example.yaml).
3. Set `SCIM_DELTA_SANDBOX_GATEWAY_BASE_URL` in GitHub.

---

## 5. GitHub Actions sequence (after smoke)

| Order | Workflow | Trigger | Purpose |
|-------|----------|---------|---------|
| 0 | Backend / frontend RC CI on RC SHA | `main` / PR | Artifacts exist |
| 1 | **GitOps Promote** or manual PR | `workflow_dispatch` / PR | Staging image tags |
| 2 | Argo sync / helm upgrade | Operator | Live staging deploy |
| 3 | **Smoke checklist §3** | Operator | Gate |
| 4 | `check-staging-pp-secrets.sh --check-github` | Local / CI | Secrets present |
| 5 | **SCIM Delta Readiness** | `workflow_dispatch` → job `scim-delta-sandbox-evidence` | PP-1 |
| 6 | **Break-glass Revocation Readiness** | `workflow_dispatch` only | PP-2 |
| 7 | **Backend pre-prod evidence bundle** | `backend-preprod-evidence-bundle.yml` or `run-backend-live-staging-pp-evidence.sh` | Aggregate |

```bash
gh workflow run scim-delta-readiness.yml -f provider=okta
gh workflow run break-glass-revocation-readiness.yml
bash scripts/security/run-backend-live-staging-pp-evidence.sh --all --rc-id <RC_ID> --provider okta --pp3-required false
```

### 5.1 PP evidence gates (hard)

| Rule | Rationale |
|------|-----------|
| **Do not** dispatch PP-1 if SM-1–SM-5 fail | False `certified` / wasted secrets |
| **Do not** dispatch PP-2 if SM-1–SM-6 fail | Drill invalid |
| **Do not** write backend bundle **GO** without PP-1 + PP-2 artifacts on RC SHA | Fixture-only **NO_GO** |
| **Do not** set backend/platform **GO** without bundle **GO** | Sign-off rules |

PP-3: default **not_required** unless production CR includes dedicated retention datasource.

---

## 6. Rollback runbook (staging)

| Step | Action | Verify |
|------|--------|--------|
| 1 | **GitOps revert** — revert PR that promoted `<RC_IMAGE_TAG>` or restore previous tag in `values.yaml` | Diff shows prior tag |
| 2 | **Argo CD rollback** (if enabled) — `argocd app rollback notebook-platform-staging <revision>` | Sync healthy |
| 3 | **Previous image tag** — re-promote last known-good immutable tag | Pods image ID changed |
| 4 | **Pod restart** (if config-only rollback) — `kubectl rollout restart deployment/...` | Rollout complete |
| 5 | **Gateway health re-check** | §2.6 curls pass |
| 6 | **Invalidate PP secrets** if rollback due to compromise | Governance doc rotation |
| 7 | **Re-run smoke** §3 before re-attempting PP | SM-1–SM-9 |

Staging rollback does **not** authorize production flag changes.

---

## 7. Do not run without successful staging deploy + smoke

| Action | Blocked when |
|--------|----------------|
| PP-1 live workflow | Deploy unhealthy or smoke SM-1–SM-5 open |
| PP-2 live workflow | Break-glass flags off or SM-6 open |
| Backend bundle **GO** | PP artifacts missing |
| Backend sign-off **GO** | Bundle **NO_GO** |
| Platform sign-off **GO** | Backend **NO_GO** |
| Production flag wave CR | Platform **NO_GO** |

---

## 8. Validation commands (repo)

```bash
bash scripts/check-no-secrets.sh
bash scripts/helm-template-check.sh
bash scripts/security/check-staging-pp-secrets.sh --print-required
bash scripts/security/check-staging-pp-secrets.sh --check-github   # expect MISSING until ops configures staging
bash scripts/security/ci-generate-platform-rc-signoff-template.sh
bash scripts/security/ci-backend-preprod-evidence-bundle.sh
```

---

## 9. Handoff to Faz 151 bootstrap step 12+

After this runbook §2–§3 complete, continue [staging-environment-bootstrap-plan.md](staging-environment-bootstrap-plan.md) from step 12 (GitHub secrets) through PP-1/PP-2 and bundle per [backend-live-staging-pp-evidence-run-checklist.md](backend-live-staging-pp-evidence-run-checklist.md).
