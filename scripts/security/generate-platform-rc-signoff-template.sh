#!/usr/bin/env bash
#
# Emit a placeholder full-platform RC sign-off markdown (Faz 148).
# No secrets, tokens, or live evidence values — operators fill from domain artifacts.
#
# Usage:
#   bash scripts/security/generate-platform-rc-signoff-template.sh [--rc-id ID] [--output PATH]
#
# Default output: stdout. Default RC id: PLATFORM-RC-PLACEHOLDER.

set -euo pipefail

RC_ID="PLATFORM-RC-PLACEHOLDER"
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
# Full Platform Release Candidate Sign-off — ${RC_ID}

Generated: ${GENERATED_AT}
Template: \`scripts/security/generate-platform-rc-signoff-template.sh\`
Reference: [platform-release-candidate-signoff.md](../docs/platform-release-candidate-signoff.md)
Checklist: [platform-release-go-no-go-checklist.md](../docs/platform-release-go-no-go-checklist.md)

> **Template only.** Attach sanitized backend and frontend CI artifacts. No live credentials, tokens, raw identity payloads, notification bodies, or connection strings in this document.

---

## 1. Release candidate identity

| Field | Value |
|-------|--------|
| Platform release candidate ID | ${RC_ID} |
| Git SHA (monorepo RC) | SHA-PLACEHOLDER |
| Backend staging image tag | IMAGE-TAG-PLACEHOLDER |
| Backend production image tag (if promoted) | PROD-TAG-PLACEHOLDER |
| Frontend build artifact reference | DIST-REF-PLACEHOLDER |
| Freeze timestamp (UTC) | FREEZE-UTC-PLACEHOLDER |
| Change request / ticket ID | TICKET-PLACEHOLDER |

---

## 2. Backend readiness summary

| Area | Verdict / status |
|------|------------------|
| Backend RC readiness | TBD |
| Backend Docker CI | TBD |
| PP bundle (\`finalRecommendation\`) | TBD |
| PP-1 SCIM | missing / pass / fail |
| PP-2 Break-glass | missing / pass / fail |
| PP-3 Retention | **not_required** (default) / pass / fail / missing |
| Backend domain sign-off | TBD |

**Backend blockers (if any):**

| ID | Blocker | Status |
|----|---------|--------|
| BB-1 | Staging secrets not provisioned | open / closed |
| BB-2 | PP-1 live evidence missing | open / closed |
| BB-3 | PP-2 live evidence missing | open / closed |
| BB-4 | PP-3 required but missing/fail | open / closed / n/a |

---

## 3. Frontend readiness summary

| Area | Verdict / status |
|------|------------------|
| Frontend RC gate | TBD |
| Vitest / TypeScript / Playwright / build | TBD |
| Visual QA checklist | **incomplete** (default) |
| Frontend domain sign-off | TBD |

**Frontend blockers (if any):**

| ID | Blocker | Status |
|----|---------|--------|
| FB-1 | Visual QA incomplete | open / closed |
| FB-2 | Frontend RC gate fail | open / closed |
| FB-3 | Major route regression (visual QA) | open / closed / n/a |

---

## 4. Gate status table

| Gate | Artifact | Verdict |
|------|----------|---------|
| Backend RC | \`backend-rc-readiness-summary.md\` | TBD |
| Backend Docker CI | \`backend-docker-ci-summary.md\` | TBD |
| Backend PP bundle | \`backend-preprod-evidence-summary.md\` | TBD |
| Frontend RC | \`frontend-rc-readiness-summary.md\` | TBD |
| Secret scan | RC gate outputs | TBD |

---

## 5. Production flag plan and rollback

| Reference | Link / value |
|-----------|----------------|
| Flag flip plan | [backend-production-flag-flip-plan.md](../docs/backend-production-flag-flip-plan.md) |
| Rollback matrix | [backend-production-rollback-matrix.md](../docs/backend-production-rollback-matrix.md) |
| First production wave selected | WAVE-PLACEHOLDER |
| Flags in first wave (names only) | LIST-PLACEHOLDER |

**Production flags enabled by this document:** none

---

## 6. Known blockers

| ID | Blocker | Owner | Status |
|----|---------|-------|--------|
| PB-1 | Backend PP live evidence not attached | TBD | open |
| PB-2 | Frontend visual QA incomplete | TBD | open |
| PB-3 | PLACEHOLDER | TBD | open / closed |

---

## 7. High risks

| ID | Risk | Mitigation | Accepted? |
|----|------|------------|-----------|
| H-1 | PLACEHOLDER | PLACEHOLDER | yes / no |

---

## 8. Accepted risks

| ID | Description | Owner | Domain |
|----|-------------|-------|--------|
| AR-1 | PLACEHOLDER | OWNER-PLACEHOLDER | backend / frontend / platform |

---

## 9. Required approvers

| Role | Name | Date (UTC) | Sign-off |
|------|------|------------|----------|
| Platform / release manager | NAME-PLACEHOLDER | DATE-PLACEHOLDER | [ ] |
| Security | NAME-PLACEHOLDER | DATE-PLACEHOLDER | [ ] |
| Backend owner | NAME-PLACEHOLDER | DATE-PLACEHOLDER | [ ] |
| Frontend owner | NAME-PLACEHOLDER | DATE-PLACEHOLDER | [ ] |
| QA (frontend visual) | NAME-PLACEHOLDER | DATE-PLACEHOLDER | [ ] |

---

## 10. Final platform decision

**Rules:** Backend **GO** + Frontend **GO** + no blocker → Platform **GO**; either domain **GO_WITH_ACCEPTED_RISKS** with approvals → Platform **GO_WITH_ACCEPTED_RISKS**; PP missing, visual QA incomplete, gate fail, secret leak → **NO_GO**.

| Field | Value |
|-------|--------|
| Backend domain decision | **NO_GO** (template) |
| Frontend domain decision | **NO_GO** (template) |
| **Platform final decision** | **NO_GO** |
| Rationale | Staging PP evidence and frontend visual QA not complete for production sign-off. |

---

## 11. Attached artifacts checklist

- [ ] \`backend-rc-readiness-results.json\` + summary
- [ ] \`backend-docker-ci-results.json\` + summary
- [ ] \`backend-preprod-evidence-bundle.json\` + summary
- [ ] \`frontend-rc-readiness-results.json\` + summary
- [ ] Completed [frontend-release-visual-qa-checklist.md](../docs/frontend-release-visual-qa-checklist.md)
- [ ] Completed [platform-release-go-no-go-checklist.md](../docs/platform-release-go-no-go-checklist.md)

EOF
}

if [[ -n "$OUTPUT" ]]; then
  emit >"$OUTPUT"
  echo "Wrote platform sign-off template: $OUTPUT"
else
  emit
fi
