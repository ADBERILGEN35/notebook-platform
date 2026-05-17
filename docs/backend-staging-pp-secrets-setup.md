# Backend staging PP secrets setup (Faz 133)

Configure **GitHub repository secrets** and optional **variables** so **SCIM Delta Readiness** and **Break-glass Revocation Readiness** workflows can produce live staging evidence. **Does not enable production flags.**

| Doc | Role |
|-----|------|
| [backend-live-staging-pp-evidence-run-checklist.md](backend-live-staging-pp-evidence-run-checklist.md) | Full run + bundle |
| [backend-staging-pp-evidence-execution-plan.md](backend-staging-pp-evidence-execution-plan.md) | Execution order |
| [scim-delta-sandbox-evidence.md](scim-delta-sandbox-evidence.md) | PP-1 details |
| [break-glass-revocation-staging-drill.md](break-glass-revocation-staging-drill.md) | PP-2 details |
| [backend-staging-pp-secrets-governance.md](backend-staging-pp-secrets-governance.md) | Ownership, TTL, rotation, cleanup (Faz 134) |

**Probe (no values printed):**

```bash
bash scripts/security/check-staging-pp-secrets.sh --check-github
```

---

## PP-1 — SCIM delta sandbox

### GitHub repository secrets (required)

| Secret | Description |
|--------|-------------|
| `SCIM_DELTA_SANDBOX_GATEWAY_BASE_URL` | Staging API gateway base URL (scheme + host, no trailing path) |
| `SCIM_DELTA_SANDBOX_ADMIN_ACCESS_TOKEN` | Platform admin JWT for SCIM diagnostics (short-lived; rotate via secret manager) |

### GitHub repository variables (optional)

| Variable | Default | Purpose |
|----------|---------|---------|
| `SCIM_DELTA_SANDBOX_PROVIDER` | `okta` | Default when dispatch input omitted |
| `SCIM_DELTA_SANDBOX_EXPECT_READY` | `false` | `true` → workflow fails if not `certified` |

### Staging cluster / IdP (not in GitHub)

| Item | Staging only | Reference |
|------|--------------|-----------|
| `SCIM_DELTA_DRY_RUN_ONLY=true` | Mandatory | Chart / overlay |
| `SCIM_DELTA_REMOTE_FETCH_ENABLED=true` | Staging | [scim-delta-remote-fetch-enable.overlay.example.yaml](../deploy/gitops/environments/staging/scim-delta-remote-fetch-enable.overlay.example.yaml) |
| IdP bearer token | ExternalSecret / Vault | [externalsecret-scim-delta-keys.example.yaml](../deploy/gitops/environments/staging/externalsecret-scim-delta-keys.example.yaml) |

**Never** commit bearer token values. Overlay enables secret **name** wiring only.

### Workflow dispatch

```bash
gh workflow run scim-delta-readiness.yml -f provider=okta
# entra | generic
```

### Expected artifact

| Artifact name | Primary JSON |
|---------------|--------------|
| `scim-delta-sandbox-evidence-{provider}` | `scim-delta-sandbox-evidence.json` |

**Pass:** `certificationResult: certified`

---

## PP-2 — Break-glass revocation drill

### GitHub repository secrets (required)

| Secret | Required | Description |
|--------|----------|-------------|
| `BREAK_GLASS_STAGING_API_BASE_URL` | Yes | Staging gateway URL |
| `BREAK_GLASS_STAGING_ADMIN_ACCESS_TOKEN` | Yes | Admin JWT (MFA for revoke API) |
| `BREAK_GLASS_STAGING_EMERGENCY_TOKEN` | One of | Static emergency login |
| `BREAK_GLASS_STAGING_BREAK_GLASS_TOKEN` | One of | Pre-issued break-glass JWT |

### GitHub repository variables (optional)

| Variable | Default |
|----------|---------|
| `BREAK_GLASS_STAGING_TEST_ENDPOINT` | `/admin/enterprise/status` |
| `BREAK_GLASS_STAGING_IMAGE_TAG` | `staging` |

### Staging flags (drill only)

See [break-glass-revocation-staging-drill.md](break-glass-revocation-staging-drill.md). Production flip plan keeps `GATEWAY_BREAK_GLASS_ADMIN_ALLOWED=false` in prod unless separate CR.

### Workflow dispatch

```bash
gh workflow run break-glass-revocation-readiness.yml
```

### Expected artifact

| Artifact name | Primary JSON |
|---------------|--------------|
| `break-glass-revocation-evidence` | `break-glass-revocation-evidence.json` |

**Pass:** `result: passed`, `gatewayRejectStatus: 401`, `gatewayRejectErrorCode: BREAK_GLASS_TOKEN_REVOKED`

---

## PP-3 — Retention staging smoke (conditional)

### When required

| Production CR includes `retentionDatasource.*.enabled`? | `pp3_required` |
|--------------------------------------------------------|----------------|
| No | `false` — skip PP-3 secrets |
| Yes | `true` — configure below |

### GitHub secrets (if PP-3 required)

| Secret | Script env at runtime |
|--------|----------------------|
| `RETENTION_STAGING_API_BASE_URL` | `API_BASE_URL` |
| `RETENTION_STAGING_ADMIN_ACCESS_TOKEN` | `ADMIN_ACCESS_TOKEN` |

### Variables (optional)

`RETENTION_EXPECT_CONTENT_READY`, `RETENTION_EXPECT_NOTIFICATION_READY`, `RETENTION_EXPECT_WORKSPACE_READY`, `RETENTION_EXPECT_SEARCH_READY` (see retention-readiness workflow).

### Dispatch

```bash
gh workflow run retention-readiness.yml -f run_staging_smoke=true
```

---

## Setting secrets (operator)

Follow [backend-staging-pp-secrets-governance.md](backend-staging-pp-secrets-governance.md) for ownership, TTL, and **mandatory post-run rotation**.

List required names (no values):

```bash
bash scripts/security/check-staging-pp-secrets.sh --print-required
```

Safe set (value from stdin only):

```bash
gh secret set SCIM_DELTA_SANDBOX_ADMIN_ACCESS_TOKEN --env staging --body-file -
gh secret set BREAK_GLASS_STAGING_EMERGENCY_TOKEN --env staging --body-file -
```

1. Open repository **Settings → Secrets and variables → Actions** (prefer **Environment: staging**).
2. Add each secret from your secret manager (1Password, Vault, cloud SM). **Do not paste into docs or tickets.**
3. Run probe:

```bash
bash scripts/security/check-staging-pp-secrets.sh --check-github
```

Expect `READY_GITHUB` for PP-1 and PP-2 when names exist.

Local-only runs (optional): export the same variable **names** in your shell for `run-backend-live-staging-pp-evidence.sh --run-pp1 --run-pp2` without committing values.

---

## Workflow dispatch + artifact download

### Dispatch (orchestrator)

```bash
bash scripts/security/run-backend-live-staging-pp-evidence.sh \
  --dispatch-github \
  --provider okta \
  --rc-id rc-2026-05-17 \
  --pp3-required false
```

### Download artifacts

After workflows complete:

```bash
bash scripts/security/download-backend-pp-artifacts.sh \
  --output pp-downloads \
  --provider okta \
  --latest
```

Manual `gh` (same result):

```bash
gh run list --workflow scim-delta-readiness.yml --limit 3
gh run download <RUN_ID> -n scim-delta-sandbox-evidence-okta -D pp-downloads/scim-delta-sandbox-evidence-okta

gh run list --workflow break-glass-revocation-readiness.yml --limit 3
gh run download <RUN_ID> -n break-glass-revocation-evidence -D pp-downloads/break-glass-revocation-evidence
```

---

## Bundle input preparation

```bash
bash scripts/security/run-backend-live-staging-pp-evidence.sh \
  --rc-id rc-2026-05-17 \
  --provider okta \
  --pp3-required false \
  --output-base live-pp-evidence-out \
  --check-github \
  --wait-downloads
```

Normalized files under `live-pp-evidence-out/pp-input/`:

- `pp-input/scim/scim-delta-sandbox-evidence.json`
- `pp-input/breakglass/break-glass-revocation-evidence.json`
- `pp-input/retention/retention-staging-smoke-results.json` (if PP-3 required)

Outputs under `live-pp-evidence-out/`:

- `backend-preprod-evidence-bundle.json`
- `backend-preprod-evidence-summary.md`
- `live-pp-evidence-run-report.md`

| `finalRecommendation` | Meaning |
|----------------------|---------|
| **GO** | Live PP pass criteria met |
| **GO_WITH_ACCEPTED_RISKS** | Documented risks in bundle |
| **NO_GO** | Missing secrets, missing artifacts, or failed PP |

---

## Readiness outcomes

| State | PP-1 / PP-2 | Bundle |
|-------|-------------|--------|
| Secrets **missing** | `MISSING_SECRET` / `NOT_RUN` | **NO_GO** |
| Secrets **present**, workflow not run | `NOT_RUN` | **NO_GO** |
| Workflow run, artifact downloaded, criteria pass | **PASS** | **GO** (if PP-2 also pass) |

---

## Privacy

- Probe and scripts print **present/missing** only.
- Do not attach tokens to CRs, sign-off docs, or chat.
- `bash scripts/check-no-secrets.sh` before any commit.
