#!/usr/bin/env bash
#
# Emit a placeholder backend RC sign-off markdown (Faz 131).
# No secrets, tokens, or live evidence values — operators fill from CI/staging artifacts.
#
# Usage:
#   bash scripts/security/generate-backend-rc-signoff-template.sh [--rc-id ID] [--output PATH]
#
# Default output: stdout. Default RC id: RC-PLACEHOLDER.

set -euo pipefail

RC_ID="RC-PLACEHOLDER"
OUTPUT=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --rc-id) RC_ID="$2"; shift 2 ;;
    --output) OUTPUT="$2"; shift 2 ;;
    -h|--help)
      echo "Usage: $0 [--rc-id ID] [--output PATH]"
      exit 0
      ;;
    *) echo "unknown arg: $1" >&2; exit 2 ;;
  esac
done

GENERATED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -u +%Y-%m-%dT%H:%M:%SZ)"

emit() {
  cat <<EOF
# Backend Release Candidate Sign-off — ${RC_ID}

Generated: ${GENERATED_AT}
Template: \`scripts/security/generate-backend-rc-signoff-template.sh\`
Reference: [backend-release-candidate-signoff.md](../docs/backend-release-candidate-signoff.md)

> **Template only.** Attach sanitized CI/staging artifacts. No tokens, JWTs, JDBC URLs, raw SCIM payloads, or Authorization headers in this document.

---

## 1. Release candidate identity

| Field | Value |
|-------|--------|
| Release candidate ID | ${RC_ID} |
| Git SHA (RC build) | SHA-PLACEHOLDER |
| Staging image tag | IMAGE-TAG-PLACEHOLDER |
| Production image tag (if promoted) | PROD-TAG-PLACEHOLDER |
| Branch / tag | BRANCH-PLACEHOLDER |
| Freeze timestamp (UTC) | FREEZE-UTC-PLACEHOLDER |
| Change request ID | CR-PLACEHOLDER |

---

## 2. Automated gate verdicts

| Gate | Artifact | Verdict |
|------|----------|---------|
| RC readiness | \`backend-rc-readiness-summary.md\` | PASS / PASS_WITH_ENVIRONMENT_SKIPS / FAIL |
| Docker CI | \`backend-docker-ci-summary.md\` | PASS / ENVIRONMENT_SKIPPED / FAIL |
| Pre-prod evidence bundle | \`backend-preprod-evidence-summary.md\` | GO / GO_WITH_ACCEPTED_RISKS / NO_GO |

**Recorded verdicts (fill from artifacts):**

| Gate | Verdict |
|------|---------|
| RC readiness | TBD |
| Docker CI | TBD |
| PP bundle | TBD |

---

## 3. Pre-production evidence (PP)

| PP | Status | Source artifact | Notes |
|----|--------|-----------------|-------|
| PP-1 SCIM delta sandbox | missing / pass / fail | \`scim-delta-sandbox-evidence.json\` | Expect \`certificationResult: certified\` |
| PP-2 Break-glass revocation drill | missing / pass / fail | \`break-glass-revocation-evidence.json\` | Expect \`result: passed\`, \`gatewayRejectErrorCode: BREAK_GLASS_TOKEN_REVOKED\` |
| PP-3 Retention dedicated datasource | not_required / missing / pass / fail | \`retention-staging-smoke-results.json\` | Required only if \`retentionDatasource\` in prod CR scope |

**PP-3 required for this RC:** yes / no

---

## 4. Production rollout references (no enables in this document)

| Reference | Link |
|-----------|------|
| Flag flip wave plan | [backend-production-flag-flip-plan.md](../docs/backend-production-flag-flip-plan.md) |
| Rollback matrix | [backend-production-rollback-matrix.md](../docs/backend-production-rollback-matrix.md) |
| Approval gate rules | [backend-production-approval-gate.md](../docs/backend-production-approval-gate.md) |
| Staging PP execution | [backend-staging-pp-evidence-execution-plan.md](../docs/backend-staging-pp-evidence-execution-plan.md) |

**Flag families in scope for post-sign-off CRs:** (check all that apply)

- [ ] Break-glass + gateway denylist
- [ ] SCIM delta remote fetch / multi-page
- [ ] Retention dedicated datasource
- [ ] Admin GitOps live provider
- [ ] Other: DESCRIPTION-PLACEHOLDER

---

## 5. Open blockers

| ID | Blocker | Status |
|----|---------|--------|
| B-1 | Live PP-1 SCIM sandbox evidence | open / closed |
| B-2 | Live PP-2 break-glass drill | open / closed |
| B-3 | Live PP-3 retention E2E (if required) | open / closed / n/a |
| B-4 | Other | open / closed |

---

## 6. Risks

### High risks

| ID | Risk | Mitigation | Accepted? |
|----|------|------------|-----------|
| H-1 | PLACEHOLDER | PLACEHOLDER | yes / no |

### Accepted risks (bundle / CR)

| ID | Description | Owner | Expiry |
|----|-------------|-------|--------|
| AR-1 | PLACEHOLDER | OWNER-PLACEHOLDER | DATE-PLACEHOLDER |

---

## 7. Attached artifacts checklist

- [ ] \`backend-rc-readiness-results.json\` + summary
- [ ] \`backend-docker-ci-results.json\` + summary
- [ ] \`backend-preprod-evidence-bundle.json\` + summary
- [ ] \`scim-delta-sandbox-evidence.json\` (+ checklist/summary if present)
- [ ] \`break-glass-revocation-evidence.json\` (+ summary)
- [ ] \`retention-staging-smoke-results.json\` (if PP-3 required)
- [ ] Freeze checklist signed: [backend-release-freeze-checklist.md](../docs/backend-release-freeze-checklist.md)

---

## 8. Operations ownership

| Role | Name / rotation | Contact |
|------|-----------------|---------|
| Rollback owner | NAME-PLACEHOLDER | CONTACT-PLACEHOLDER |
| Monitoring owner | NAME-PLACEHOLDER | CONTACT-PLACEHOLDER |
| On-call / incident | NAME-PLACEHOLDER | CONTACT-PLACEHOLDER |

---

## 9. Final decision

**Rules:** RC \`PASS\` or \`PASS_WITH_ENVIRONMENT_SKIPS\` + Docker CI \`PASS\` + bundle \`GO\` → \`GO\`; bundle \`GO_WITH_ACCEPTED_RISKS\` → \`GO_WITH_ACCEPTED_RISKS\`; otherwise \`NO_GO\`. Privacy violation in any artifact → \`NO_GO\`.

| Field | Value |
|-------|--------|
| **Final decision** | **NO_GO** |
| Rationale | Template default — replace after live artifacts attached and gates verified. |

### Approvers

| Role | Name | Date (UTC) | Sign-off |
|------|------|------------|----------|
| Platform / release manager | NAME-PLACEHOLDER | DATE-PLACEHOLDER | [ ] |
| Security | NAME-PLACEHOLDER | DATE-PLACEHOLDER | [ ] |
| Identity / integration (if SCIM in scope) | NAME-PLACEHOLDER | DATE-PLACEHOLDER | [ ] |
| DBA (if retention datasource in scope) | NAME-PLACEHOLDER | DATE-PLACEHOLDER | [ ] |

---

## 10. Threat model acknowledgment

Reviewed for this RC: [security-threat-model.md](../docs/security-threat-model.md) (relevant Faz sections for flags in scope).

EOF
}

if [[ -n "$OUTPUT" ]]; then
  emit >"$OUTPUT"
  echo "Wrote sign-off template: $OUTPUT"
else
  emit
fi
