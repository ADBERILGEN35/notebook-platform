# Backend release freeze checklist (Faz 131)

Process checklist from **RC freeze** through **production flag CRs**. Pair with [backend-release-candidate-signoff.md](backend-release-candidate-signoff.md).

**Freeze** means: no new backend features or unapproved production flag changes on the RC line until sign-off completes or the RC is abandoned.

---

## Freeze decision rules

| Check | If failed |
|-------|-----------|
| Any production flag changed outside an approved CR | **Do not sign off** — revert GitOps or open emergency CR |
| New backend feature merged after freeze timestamp | **Re-freeze** or bump RC ID |
| Dangerous default enabled in chart/prod overlay without validation | **NO_GO** — run `validate-production-flag-plan.sh` |
| Secret or token pattern in repo | **NO_GO** — fix before merge |
| RC / Docker / PP artifacts missing for RC SHA | **NO_GO** — run workflows |
| PP bundle not `GO` (or signed `GO_WITH_ACCEPTED_RISKS`) | **NO_GO** for flag flip |
| Rollback / monitoring / on-call owners not assigned | **Hold sign-off** until named |

---

## Phase A — Before freeze

| # | Item | Owner | Done |
|---|------|-------|------|
| A1 | RC ID and target Git SHA / image tag agreed | Release manager | [ ] |
| A2 | Scope of production flag families documented in CR draft | Platform | [ ] |
| A3 | `bash scripts/check-no-secrets.sh` PASS on RC branch | CI / dev | [ ] |
| A4 | `ci-backend-rc-readiness.sh` or **Backend RC Readiness** scheduled on RC SHA | CI | [ ] |
| A5 | **Backend Docker CI** scheduled on RC SHA (not local skip only) | CI | [ ] |
| A6 | Staging PP runbook reviewed: [backend-staging-pp-evidence-execution-plan.md](backend-staging-pp-evidence-execution-plan.md) | Ops | [ ] |

---

## Phase B — At freeze (RC line locked)

| # | Item | Owner | Done |
|---|------|-------|------|
| B1 | **No production flag changed** outside approved CR (chart `values.yaml` + `deploy/gitops/environments/prod/values.yaml`) | Platform | [ ] |
| B2 | **No new backend feature** after freeze timestamp on RC branch (hotfix exception requires release manager approval) | Engineering | [ ] |
| B3 | **No unresolved dangerous default** — `ci-backend-production-readiness-review.sh` PASS | CI | [ ] |
| B4 | **No secret in repo** — `check-no-secrets.sh` PASS | CI | [ ] |
| B5 | Freeze timestamp recorded in sign-off package (UTC) | Release manager | [ ] |
| B6 | Only allowlisted cherry-picks documented (if any) | Release manager | [ ] |

---

## Phase C — Evidence attachment (post-freeze, pre-sign-off)

| # | Item | Owner | Done |
|---|------|-------|------|
| C1 | **RC gate artifact attached** — `backend-rc-readiness-summary.md` (+ JSON) for RC SHA | CI / ops | [ ] |
| C2 | **Docker CI artifact attached** — `backend-docker-ci-summary.md` (+ JSON) for RC SHA | CI / ops | [ ] |
| C3 | **PP bundle attached** — live `backend-preprod-evidence-bundle.json` + summary (not fixture-only) | Ops | [ ] |
| C4 | PP-1 / PP-2 upstream JSON attached per bundle references | Ops | [ ] |
| C5 | PP-3 attached if `pp3_required=true` for this RC | Ops | [ ] |
| C6 | Sign-off markdown completed from template | Release manager | [ ] |

---

## Phase D — Operations readiness

| # | Item | Owner | Done |
|---|------|-------|------|
| D1 | **Rollback owner** assigned (see [backend-production-rollback-matrix.md](backend-production-rollback-matrix.md)) | Platform | [ ] |
| D2 | **Monitoring owner** assigned (dashboards/alerts for flags in scope) | SRE | [ ] |
| D3 | **On-call / incident contact** assigned for flip window | On-call lead | [ ] |
| D4 | Rollback steps copied into CR for each flag family | Platform | [ ] |
| D5 | 24h monitoring plan acknowledged ([flag flip plan](backend-production-flag-flip-plan.md)) | SRE | [ ] |

---

## Phase E — Sign-off

| # | Item | Owner | Done |
|---|------|-------|------|
| E1 | Final decision matches [sign-off rules](backend-release-candidate-signoff.md#final-decision-rules) | Release manager | [ ] |
| E2 | Approver signatures recorded (platform + security minimum) | Approvers | [ ] |
| E3 | Threat model sections reviewed for in-scope flags ([security-threat-model.md](security-threat-model.md)) | Security | [ ] |
| E4 | If `GO_WITH_ACCEPTED_RISKS`, each accepted risk has owner + expiry | Security | [ ] |

---

## Phase F — After sign-off (still no ad-hoc prod flags)

| # | Item | Owner | Done |
|---|------|-------|------|
| F1 | Production enables only via **one wave per CR** | Platform | [ ] |
| F2 | `validate-production-flag-plan.sh` PASS on each GitOps PR | CI | [ ] |
| F3 | Post-sync smoke per flip plan | Ops | [ ] |
| F4 | Incident channel and rollback owner on standby for 24h | On-call | [ ] |

---

## Signatures (freeze + evidence complete)

| Role | Name | Date (UTC) | Signature |
|------|------|------------|-----------|
| Release manager | | | [ ] |
| Security | | | [ ] |
| Platform / SRE | | | [ ] |
