# Backend staging PP secrets governance and rotation (Faz 134)

Governance for **PP-1** (SCIM delta sandbox) and **PP-2** (break-glass revocation drill) credentials used by GitHub Actions and staging cluster overlays. **Does not enable production flags.**

| Doc | Role |
|-----|------|
| [staging-environment-bootstrap-plan.md](staging-environment-bootstrap-plan.md) | Staging infra + bring-up before secrets (Faz 151) |
| [backend-staging-pp-secrets-setup.md](backend-staging-pp-secrets-setup.md) | Setup + dispatch |
| [backend-live-staging-pp-evidence-run-checklist.md](backend-live-staging-pp-evidence-run-checklist.md) | Run + bundle |
| [backend-staging-pp-secrets-governance.md](backend-staging-pp-secrets-governance.md) | This doc — ownership, TTL, rotation, cleanup |

**Catalog (no values):** `bash scripts/security/check-staging-pp-secrets.sh --print-required`

---

## Principles

| Rule | Requirement |
|------|-------------|
| Staging only | No production GitHub environment secrets for PP drills unless separate approved CR |
| Shortest TTL | Emergency/break-glass credentials: minimum lifetime for one drill window |
| Rotate after run | Remove or rotate every secret used in a live evidence run |
| No persistence in repo | Never commit tokens; `check-no-secrets.sh` on every PR |
| Manual dispatch | PP live jobs: `workflow_dispatch` only — not PR checks |
| Environment scope | Prefer **GitHub Environment `staging`** secrets over repo-wide when available |
| Evidence hygiene | Artifacts sanitized; privacy validators before bundle **GO** |

---

## GitHub storage model

| Layer | Preferred | Notes |
|-------|-----------|--------|
| Actions secrets | Environment `staging` | Limits blast radius vs repository secrets |
| Actions variables | Repository or environment | Non-sensitive defaults only (provider name, endpoints path) |
| Cluster IdP bearer | ExternalSecret → Vault | Not stored in GitHub; see staging overlay examples |

### Safe creation (never paste values in shell history)

**Repository secret** (use only if environment secrets unavailable):

```bash
# Read value from stdin / secret manager — do not echo
gh secret set SCIM_DELTA_SANDBOX_ADMIN_ACCESS_TOKEN --body-file -
```

**Environment-scoped secret** (preferred):

```bash
gh secret set SCIM_DELTA_SANDBOX_ADMIN_ACCESS_TOKEN --env staging --body-file -
```

**Variable** (non-secret default):

```bash
gh variable set SCIM_DELTA_SANDBOX_PROVIDER --body "okta"
gh variable set SCIM_DELTA_SANDBOX_EXPECT_READY --body "false"
gh variable set BREAK_GLASS_STAGING_TEST_ENDPOINT --body "/admin/enterprise/status"
```

Remove or rotate after evidence run:

```bash
gh secret delete SCIM_DELTA_SANDBOX_ADMIN_ACCESS_TOKEN --env staging
# or rotate via --body-file - with new value from secret manager
```

---

## PP-1 — SCIM delta sandbox

### GitHub secrets and variables

| Name | Type | Owner | Source system | Scope | TTL / expiry | Rotation trigger | Post-run cleanup | Where stored | Allowed workflow | Forbidden usage |
|------|------|-------|---------------|-------|--------------|------------------|------------------|--------------|------------------|-----------------|
| `SCIM_DELTA_SANDBOX_GATEWAY_BASE_URL` | Secret or variable | Platform / SRE | Staging ingress DNS | Staging gateway only | Until URL changes | Staging URL change | Update secret if URL changes | GitHub `staging` env | `scim-delta-readiness` dispatch / staging push | Production URL; logging full URL in tickets with creds |
| `SCIM_DELTA_SANDBOX_ADMIN_ACCESS_TOKEN` | Secret | Identity + security | IdP/admin token broker | SCIM diagnostics admin | **≤ 24h** or single-run | After each evidence run; on leak suspicion | **Revoke JWT** in identity; delete or rotate GitHub secret | GitHub `staging` env | `scim-delta-readiness` job `scim-delta-sandbox-evidence` | Long-lived prod admin; CI logs; CR attachments |
| `SCIM_DELTA_SANDBOX_PROVIDER` | Variable | Integration owner | Runbook choice | okta / entra / generic | N/A (non-secret) | IdP change | N/A | GitHub variable | Dispatch input default | Storing bearer here |
| `SCIM_DELTA_SANDBOX_EXPECT_READY` | Variable | Release manager | Process | CI gate strictness | N/A | Before RC sign-off | Reset to `false` after run if set `true` | GitHub variable | Same workflow | N/A |

### Cluster / IdP (ExternalSecret)

| Name | Owner | Source | Scope | TTL | Rotation | Post-run | Where | Allowed | Forbidden |
|------|-------|--------|-------|-----|----------|----------|-------|---------|-----------|
| `scimDeltaRemoteFetch.bearerTokenFromSecret` | Integration + security | IdP sandbox app | Staging cluster dry-run fetch | Per IdP sandbox policy | IdP key rollover; after certification run | Rotate sandbox app secret in Vault; confirm ExternalSecret sync | Vault + ESO | Staging remote fetch dry-run | Prod IdP; commit bearer in GitOps |

References: [externalsecret-scim-delta-keys.example.yaml](../deploy/gitops/environments/staging/externalsecret-scim-delta-keys.example.yaml), [scim-delta-remote-fetch-enable.overlay.example.yaml](../deploy/gitops/environments/staging/scim-delta-remote-fetch-enable.overlay.example.yaml).

**Governance decision:** Sandbox-only, read-only SCIM, `SCIM_DELTA_DRY_RUN_ONLY=true`; rotate admin token and IdP bearer after run or weekly whichever is sooner.

---

## PP-2 — Break-glass revocation drill

| Name | Type | Owner | Source system | Scope | TTL / expiry | Rotation trigger | Post-run cleanup | Where stored | Allowed workflow | Forbidden usage |
|------|------|-------|---------------|-------|--------------|------------------|------------------|--------------|------------------|-----------------|
| `BREAK_GLASS_STAGING_API_BASE_URL` | Secret or variable | Platform / SRE | Staging ingress | Staging gateway | Until URL change | URL change | Update if needed | GitHub `staging` env | `break-glass-revocation-readiness` **dispatch only** | Production |
| `BREAK_GLASS_STAGING_ADMIN_ACCESS_TOKEN` | Secret | Security + on-call | Admin user / token broker | Revoke API + MFA step-up | **≤ 8h** or single drill | After every drill; leak suspicion | **Revoke sessions**; rotate GitHub secret | GitHub `staging` env | Break-glass drill job only | Shared prod admin; permanent PAT |
| `BREAK_GLASS_STAGING_EMERGENCY_TOKEN` | Secret (one of) | Security | Break-glass static/emergency issuance | Staging login only | **Shortest possible** (1 drill window) | **Immediately after drill** | Invalidate in identity; rotate hash; delete GH secret | GitHub `staging` env | Same drill job | Prod enable; reuse across drills |
| `BREAK_GLASS_STAGING_BREAK_GLASS_TOKEN` | Secret (one of) | Security | Pre-issued BG JWT | Staging drill | Until `jti` revoked | After drill (revoke + new issuance) | Confirm denylist; rotate secret | GitHub `staging` env | Same drill job | Storing JWT in evidence |
| `BREAK_GLASS_STAGING_TEST_ENDPOINT` | Variable | Platform | Runbook | Admin GET path | N/A | Route change | N/A | GitHub variable | Drill job | Writable admin routes in test |
| `BREAK_GLASS_STAGING_IMAGE_TAG` | Variable | Release manager | RC image | Metadata in evidence | N/A | Per RC | N/A | GitHub variable | Drill job | N/A |

**Governance decision:** Staging-only; emergency token **shortest TTL**; **mandatory rotate-after-run**; never enable `GATEWAY_BREAK_GLASS_ADMIN_ALLOWED` in production without separate CR ([backend-production-flag-flip-plan.md](backend-production-flag-flip-plan.md)).

---

## PP-3 — Retention (reference, when `pp3_required=true`)

| Name | Owner | TTL | Rotation | Post-run |
|------|-------|-----|----------|----------|
| `RETENTION_STAGING_API_BASE_URL` | Platform | URL lifetime | On URL change | Update secret |
| `RETENTION_STAGING_ADMIN_ACCESS_TOKEN` | DBA + security | ≤ 24h | After smoke run | Revoke + rotate |

Default RC: **pp3_required=false** — no retention GitHub secrets required.

---

## Workflow permissions

| Workflow | PR / main push | Live evidence |
|----------|----------------|---------------|
| `scim-delta-readiness.yml` | Fixtures only on PR/main | `workflow_dispatch` or `staging` push → sandbox job |
| `break-glass-revocation-readiness.yml` | Fixtures only | **`workflow_dispatch` only** |
| `retention-readiness.yml` | Fixtures | `workflow_dispatch` + `run_staging_smoke=true` |

Operators must not add repository secrets to PR workflows. Fork PRs must not receive secrets.

---

## Post-run cleanup checklist

Complete within **24 hours** of live PP run (sooner for break-glass).

| # | Action | PP | Owner |
|---|--------|-----|-------|
| C1 | Revoke or expire **admin access token** used in workflow | PP-1, PP-2 | Identity |
| C2 | Rotate **emergency / break-glass token**; verify old `jti` on denylist | PP-2 | Security |
| C3 | Rotate **IdP sandbox SCIM bearer** in Vault; ESO reload | PP-1 | Integration |
| C4 | Delete or rotate **GitHub Actions secrets** (`gh secret delete` or `--body-file -`) | PP-1, PP-2 | Platform |
| C5 | Set `SCIM_DELTA_SANDBOX_EXPECT_READY` back to `false` if temporarily `true` | PP-1 | Release manager |
| C6 | Run artifact privacy validation on downloaded JSON | All | Ops |
| C7 | Confirm evidence attachments contain **no** tokens (spot check + bundle builder) | All | Security |
| C8 | Record rotation ticket ID in CR (not secret values) | All | Release manager |

```bash
bash scripts/scim/validate-scim-delta-evidence.sh path/to/scim-delta-sandbox-evidence.json
bash scripts/security/validate-break-glass-revocation-evidence.sh path/to/break-glass-revocation-evidence.json
bash scripts/check-no-secrets.sh
```

---

## Incident and fallback

### Secret leak suspicion

1. **Assume compromise** — revoke tokens in identity/Vault first.
2. Rotate all PP GitHub secrets in `staging` environment.
3. Invalidate active break-glass `jti` / denylist refresh.
4. Re-run `check-no-secrets.sh` on repo; scan CI logs for accidental echo (GitHub secret masking).
5. Do **not** mark bundle **GO** until new evidence from rotated credentials.
6. Open security incident per org policy; reference [security-threat-model.md](security-threat-model.md).

### Workflow accidental run

1. Cancel in-progress `gh run cancel <RUN_ID>`.
2. Delete uploaded artifacts if run completed: GitHub UI → Actions → run → Artifacts.
3. Execute post-run cleanup (C1–C4) even if drill incomplete.
4. Document in CR: accidental run, no production impact.

### PP drill failed due to auth

| Symptom | Likely cause | Action |
|---------|--------------|--------|
| SCIM `skipped` | Missing/expired GitHub secret | Probe: `check-staging-pp-secrets.sh --check-github` |
| SCIM `needs-review` | IdP bearer or staging flags | Check overlay + ExternalSecret sync |
| Break-glass `skipped` | Missing emergency/admin secret | Re-set secrets; shortest TTL |
| Break-glass `failed` (token still works) | Denylist/cache | See [break-glass-revocation-staging-drill.md](break-glass-revocation-staging-drill.md) |
| `401` without `BREAK_GLASS_TOKEN_REVOKED` | Wrong test endpoint or flags | Verify `BREAK_GLASS_STAGING_TEST_ENDPOINT` |

Never extend TTL to “fix” a failed drill without security approval.

---

## Access control

| Role | May set GitHub secrets | May dispatch PP workflows | May download artifacts |
|------|------------------------|-------------------------|------------------------|
| Release manager | No (request via ticket) | Yes | Yes |
| Security | Yes (staging env) | Yes | Yes |
| Integration owner | IdP/Vault bearer only | PP-1 dispatch | Yes |
| Default developer | No | No | No |

Use branch protection and environment reviewers on `staging` when using environment secrets.

---

## Alignment with evidence bundle

| Secret state | PP status | Bundle |
|--------------|-----------|--------|
| Missing | `MISSING_SECRET` / `NOT_RUN` | **NO_GO** |
| Present, workflow not run | `NOT_RUN` | **NO_GO** |
| Present, run pass criteria | `PASS` | Contributes to **GO** |

Missing evidence must never be reported as **GO** in sign-off.

---

## Related threat model

See [security-threat-model.md](security-threat-model.md) § Staging PP secrets governance (Faz 134).
