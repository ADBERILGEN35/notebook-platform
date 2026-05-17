# Backend production change request template (Faz 126)

Copy into your change-management system (Jira, ServiceNow, GitHub Issue). **Do not merge GitOps prod changes without completed sections.**

---

## Header

| Field | Value |
|-------|-------|
| **CR ID** | `CR-____` |
| **Title** | Backend prod flag flip — Wave ___ : _______________ |
| **Release candidate** | `rc-________` / image tag `________` |
| **Git SHA** | `________` |
| **Environment** | production |
| **Change type** | Standard / Normal (flag enable) |
| **Planned window** | ____-__-__ __:__ UTC – __:__ UTC |

---

## 1. Pre-prod evidence bundle (required)

| Check | Status |
|-------|--------|
| `backend-preprod-evidence-bundle.json` attached | ☐ |
| `backend-preprod-evidence-summary.md` attached | ☐ |
| `finalRecommendation` | ☐ `GO` ☐ `GO_WITH_ACCEPTED_RISKS` (security sign-off attached) |
| PP-1 SCIM artifact (`scim-delta-sandbox-evidence.json`) | ☐ N/A ☐ attached |
| PP-2 Break-glass artifact (`break-glass-revocation-evidence.json`) | ☐ N/A ☐ attached |
| PP-3 Retention artifact (`retention-staging-smoke-results.json`) | ☐ N/A ☐ attached ☐ `PP-3 not required` |

**Bundle builder run ID / workflow:** ________________  
**If `NO_GO`:** do not proceed.

---

## 2. Flag wave (this CR)

Reference: [`backend-production-flag-flip-plan.md`](backend-production-flag-flip-plan.md)

| Helm key | Current | Target | Wave |
|----------|---------|--------|------|
| | `false` | | |
| | | | |

**Forbidden (confirm all remain false):**

- ☐ `scimDeltaSyncEnabled` stays `false`
- ☐ `breakGlassAllowAdminWrite` stays `false`
- ☐ `gatewayBreakGlassAdminAllowed` stays `false`
- ☐ `FRONTEND_NOTIFICATION_RETENTION_PURGE_ENABLED` stays `false`
- ☐ No GitOps auto-merge
- ☐ `scimDeltaDryRunOnly` stays `true` if remote fetch enabled

---

## 3. Approvals

| Role | Name | Date | Signature |
|------|------|------|-----------|
| Change author | | | |
| Security | | | |
| Platform / SRE | | | |
| Identity (SCIM waves) | | | N/A |
| DBA (retention waves) | | | N/A |
| On-call lead (break-glass waves) | | | N/A |

---

## 4. Blast radius & communication

| Item | Detail |
|------|--------|
| Affected services | ☐ identity ☐ gateway ☐ content ☐ notification ☐ workspace ☐ search ☐ frontend |
| Customer impact | ☐ none ☐ read-only diagnostics ☐ admin-only |
| Notification | #channel ________ ; status page ☐ yes ☐ no |

---

## 5. Implementation

| Step | Owner | Done |
|------|-------|------|
| GitOps PR link | | ☐ |
| `validate-production-flag-plan.sh` PASS on PR branch | | ☐ |
| Argo CD sync | | ☐ |
| Post-flip smoke (list commands) | | ☐ |

**GitOps PR:** ________________  
**Argo app revision after sync:** ________________

---

## 6. Rollback plan

Reference: [`backend-production-rollback-matrix.md`](backend-production-rollback-matrix.md)

| Rollback trigger | Action |
|------------------|--------|
| Smoke failure | Revert GitOps PR #___ |
| Gateway denylist issue | `gatewayBreakGlassDenylistCheckEnabled=false` + wait ___s TTL |
| Retention datasource | `retentionDatasource.<svc>.enabled=false` + pod restart |
| Code defect | Argo rollback to revision ___ |

**Rollback PR pre-staged:** ☐ yes ☐ no — link: ________

---

## 7. Monitoring (24h watch)

| Signal | Dashboard / alert |
|--------|-------------------|
| | |
| | |

---

## 8. Post-implementation

| Item | Completed |
|------|-----------|
| Smoke passed | ☐ |
| No privacy violations in logs | ☐ |
| CR closed | ☐ |
| Runbook / phase doc updated if deviation | ☐ |

---

## 9. Notes

_Free text — link incident, accepted risks from bundle, provider name for SCIM, etc._
