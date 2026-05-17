# Backend live staging PP evidence run checklist (Faz 132 / Faz 135)

Operator checklist to collect **live** PP-1 and PP-2 evidence from staging, optionally PP-3, and build the pre-prod evidence bundle. **Does not enable production flags.**

| Doc | Role |
|-----|------|
| [backend-staging-pp-evidence-execution-plan.md](backend-staging-pp-evidence-execution-plan.md) | Full runbook |
| [backend-preprod-evidence-bundle.md](backend-preprod-evidence-bundle.md) | Bundle schema |
| [backend-release-candidate-signoff.md](backend-release-candidate-signoff.md) | Post-bundle sign-off |

**Secrets setup:** [backend-staging-pp-secrets-setup.md](backend-staging-pp-secrets-setup.md) (Faz 133)  
**Orchestrator (local):** `bash scripts/security/run-backend-live-staging-pp-evidence.sh`  
**Secret probe:** `bash scripts/security/check-staging-pp-secrets.sh` (present/missing only)  
**Artifact download:** `bash scripts/security/download-backend-pp-artifacts.sh`

### One-shot orchestrator (Faz 135)

```bash
bash scripts/security/run-backend-live-staging-pp-evidence.sh \
  --all \
  --rc-id rc-2026-05-17 \
  --provider okta \
  --pp3-required false \
  --output-base live-pp-evidence-out \
  --check-github \
  --wait-downloads
```

| Mode | Flag |
|------|------|
| Probe only | `--probe-only` |
| Dispatch | `--dispatch-github` (skipped if `MISSING_SECRET`) |
| Download | `--download-artifacts` [`--wait-downloads`] |
| Bundle | `--build-bundle` |
| Full pipeline | `--all` |

**Standard PP input layout** (under `--output-base`):

- `pp-input/scim/scim-delta-sandbox-evidence.json`
- `pp-input/breakglass/break-glass-revocation-evidence.json`
- `pp-input/retention/retention-staging-smoke-results.json` (if PP-3 required)

**Final outputs:** `live-pp-evidence-run-report.md`, `backend-preprod-evidence-bundle.json`, `backend-preprod-evidence-summary.md`, `download-manifest.json` (when downloaded).

---

## Before you start

| # | Check | Done |
|---|-------|------|
| 0.1 | RC + Docker CI artifacts exist for RC SHA | [ ] |
| 0.2 | Staging Argo CD healthy; RC image deployed | [ ] |
| 0.3 | `bash scripts/check-no-secrets.sh` PASS | [ ] |
| 0.4 | Production CR scope documented (PP-3 yes/no) | [ ] |
| 0.5 | GitHub repo secrets configured (see below) | [ ] |

**PP-3 decision**

| Production CR includes `retentionDatasource.*.enabled`? | `pp3_required` |
|--------------------------------------------------------|----------------|
| **No** (default backend RC) | `false` — PP-3 `not_required` |
| **Yes** | `true` — run retention smoke + attach artifact |

---

## PP-1 — SCIM Delta Readiness (live)

### Workflow dispatch

| Field | Value |
|-------|--------|
| Workflow file | `.github/workflows/scim-delta-readiness.yml` |
| Workflow name | **SCIM Delta Readiness** |
| Job | `scim-delta-sandbox-evidence` |
| Trigger | `workflow_dispatch` |

```bash
gh workflow run scim-delta-readiness.yml -f provider=okta
# or: entra | generic
```

### Required GitHub secrets

| Secret | Required |
|--------|----------|
| `SCIM_DELTA_SANDBOX_GATEWAY_BASE_URL` | Yes |
| `SCIM_DELTA_SANDBOX_ADMIN_ACCESS_TOKEN` | Yes |

### Optional variables

| Variable | Default |
|----------|---------|
| `SCIM_DELTA_SANDBOX_PROVIDER` | `okta` |
| `SCIM_DELTA_SANDBOX_EXPECT_READY` | `false` (set `true` to fail job if not certified) |

### Staging cluster (before run)

- `SCIM_DELTA_DRY_RUN_ONLY=true` (mandatory)
- `SCIM_DELTA_REMOTE_FETCH_ENABLED=true` on staging only
- IdP bearer via ExternalSecret — never commit values

See [scim-delta-sandbox-evidence.md](scim-delta-sandbox-evidence.md).

### Expected artifact

| GitHub artifact name | Primary file |
|---------------------|--------------|
| `scim-delta-sandbox-evidence-{provider}` | `scim-delta-sandbox-evidence.json` |

Also: `scim-delta-certification-checklist.md`, `scim-delta-sandbox-summary.md`.

### Pass criteria (bundle)

| Field | Required |
|-------|----------|
| `certificationResult` | **`certified`** |

| Outcome | Run status | Bundle |
|---------|------------|--------|
| `certified` | **PASS** | PP-1 pass |
| `needs-review` | **FAIL** | NO_GO |
| `skipped` / missing secrets | **NOT_RUN** / **MISSING_SECRET** | NO_GO |
| `blocked` | **FAIL** | NO_GO |
| privacy violation | **FAIL** | NO_GO |

### Local alternative (env on operator machine only)

```bash
export SCIM_DELTA_SANDBOX_GATEWAY_BASE_URL=https://api.staging.example.com
export SCIM_DELTA_SANDBOX_ADMIN_ACCESS_TOKEN=<from-secret-manager>
export SCIM_DELTA_SANDBOX_PROVIDER=okta
bash scripts/scim/run-scim-delta-sandbox-evidence.sh
```

---

## PP-2 — Break-glass Revocation Readiness (live)

### Workflow dispatch

| Field | Value |
|-------|--------|
| Workflow file | `.github/workflows/break-glass-revocation-readiness.yml` |
| Workflow name | **Break-glass Revocation Readiness** |
| Job | `break-glass-revocation-drill` |
| Trigger | **`workflow_dispatch` only** |

```bash
gh workflow run break-glass-revocation-readiness.yml
```

### Required GitHub secrets

| Secret | Required |
|--------|----------|
| `BREAK_GLASS_STAGING_API_BASE_URL` | Yes |
| `BREAK_GLASS_STAGING_ADMIN_ACCESS_TOKEN` | Yes |
| `BREAK_GLASS_STAGING_EMERGENCY_TOKEN` | One of |
| `BREAK_GLASS_STAGING_BREAK_GLASS_TOKEN` | One of |

### Expected artifact

| Artifact name | Primary file |
|---------------|--------------|
| `break-glass-revocation-evidence` | `break-glass-revocation-evidence.json` |

### Pass criteria (bundle)

| Field | Required |
|-------|----------|
| `result` | **`passed`** |
| `gatewayRejectStatus` | **`401`** |
| `gatewayRejectErrorCode` | **`BREAK_GLASS_TOKEN_REVOKED`** |

| Outcome | Run status | Bundle |
|---------|------------|--------|
| All fields match | **PASS** | PP-2 pass |
| `skipped` / no secrets | **NOT_RUN** / **MISSING_SECRET** | NO_GO |
| `failed` / `readiness-gap` | **FAIL** | NO_GO |
| `privacy-failure` | **FAIL** | NO_GO |

See [break-glass-revocation-staging-drill.md](break-glass-revocation-staging-drill.md).

---

## PP-3 — Retention Readiness (conditional)

### When to run

| `pp3_required` | Action |
|----------------|--------|
| `false` | **Skip** — record `not_required` in bundle |
| `true` | Run smoke below |

### Workflow dispatch

```bash
gh workflow run retention-readiness.yml -f run_staging_smoke=true
```

### Required secrets (if PP-3 required)

| Secret | Maps to script env |
|--------|-------------------|
| `RETENTION_STAGING_API_BASE_URL` | `API_BASE_URL` |
| `RETENTION_STAGING_ADMIN_ACCESS_TOKEN` | `ADMIN_ACCESS_TOKEN` |

### Expected artifact

| Artifact name | Primary file |
|---------------|--------------|
| `retention-staging-smoke-evidence` | `retention-staging-smoke-results.json` |

### Pass criteria (when required)

- `overall.status`: **`passed`**
- Each `domains[].status`: **`passed`**

---

## Step 3 — Download and normalize (pp-input layout)

```bash
bash scripts/security/download-backend-pp-artifacts.sh \
  --output-base live-pp-evidence-out \
  --provider okta \
  --latest \
  --wait
```

Or let `--all` / `--download-artifacts` call the downloader. Manifest: `live-pp-evidence-out/download-manifest.json` (run ids only, no secrets).

---

## Step 4 — Build pre-prod evidence bundle

```bash
bash scripts/security/run-backend-live-staging-pp-evidence.sh \
  --build-bundle \
  --rc-id rc-2026-05-17 \
  --provider okta \
  --pp3-required false \
  --output-base live-pp-evidence-out
```

Paths used automatically:

- `pp-input/scim/scim-delta-sandbox-evidence.json`
- `pp-input/breakglass/break-glass-revocation-evidence.json`

### Manual bundle build (advanced)

```bash
export PREPROD_BUNDLE_OUTPUT_DIR=live-pp-evidence-out
export RELEASE_CANDIDATE_ID=rc-2026-05-17
export PREPROD_PP3_REQUIRED=false
export PREPROD_SCIM_EVIDENCE_PATH=live-pp-evidence-out/pp-input/scim/scim-delta-sandbox-evidence.json
export PREPROD_BREAK_GLASS_EVIDENCE_PATH=live-pp-evidence-out/pp-input/breakglass/break-glass-revocation-evidence.json
bash scripts/security/build-backend-preprod-evidence-bundle.sh
```

### Bundle verdict

| `finalRecommendation` | Meaning |
|----------------------|---------|
| **GO** | PP gates pass; may proceed to RC sign-off update |
| **GO_WITH_ACCEPTED_RISKS** | Documented risks; security sign-off required |
| **NO_GO** | Do not imply production-ready; fix evidence |

**Never treat fixture-only or missing-input bundle as production GO.**

---

## Step 5 — Update RC sign-off (after bundle)

1. Open sign-off from [generate-backend-rc-signoff-template.sh](../scripts/security/generate-backend-rc-signoff-template.sh) or existing file.
2. Copy from `backend-preprod-evidence-bundle.json`:
   - `finalRecommendation` → PP bundle verdict + final decision (with RC/Docker gates)
   - `gates.pp1.status`, `gates.pp2.status`, `gates.pp3.status`
3. Set **final decision** per [backend-release-candidate-signoff.md](backend-release-candidate-signoff.md#final-decision-rules):
   - Bundle `GO` + RC `PASS` + Docker `PASS` → **GO**
   - Bundle `GO_WITH_ACCEPTED_RISKS` → **GO_WITH_ACCEPTED_RISKS**
   - Else **NO_GO**
4. Attach bundle + PP JSON + RC + Docker summaries to CR.
5. Complete [freeze checklist](backend-release-freeze-checklist.md).

---

## Run status codes (Faz 132 report)

| Status | Meaning |
|--------|---------|
| **PASS** | Live run completed; JSON meets pass criteria |
| **FAIL** | Live run completed; criteria not met |
| **NOT_RUN** | Operator did not execute (no `--run-pp*` / no workflow) |
| **MISSING_SECRET** | Required env/GitHub secret absent |
| **SKIPPED** | Script graceful skip (e.g. retention without URL) |
| **NOT_REQUIRED** | PP-3 waived |

---

## Privacy

- Do not paste tokens into sign-off markdown, CR text, or chat.
- Run `bash scripts/check-no-secrets.sh` before commit.
- Bundle builder scans outputs for forbidden patterns; exit **3** on violation.
