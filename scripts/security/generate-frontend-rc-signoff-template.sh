#!/usr/bin/env bash
#
# Emit a placeholder frontend RC sign-off markdown (Faz 147).
# No secrets, tokens, or live evidence values — operators fill from CI artifacts + visual QA.
#
# Usage:
#   bash scripts/security/generate-frontend-rc-signoff-template.sh [--rc-id ID] [--output PATH]
#
# Default output: stdout. Default RC id: FE-RC-PLACEHOLDER.

set -euo pipefail

RC_ID="FE-RC-PLACEHOLDER"
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
# Frontend Release Candidate Sign-off — ${RC_ID}

Generated: ${GENERATED_AT}
Template: \`scripts/security/generate-frontend-rc-signoff-template.sh\`
Reference: [frontend-release-candidate-signoff.md](../docs/frontend-release-candidate-signoff.md)
Visual QA: [frontend-release-visual-qa-checklist.md](../docs/frontend-release-visual-qa-checklist.md)

> **Template only.** Attach sanitized CI artifacts and completed visual QA checklist. No live credentials, tokens, raw identity payloads, notification bodies, or connection strings in this document.

---

## 1. Release candidate identity

| Field | Value |
|-------|--------|
| Release candidate ID | ${RC_ID} |
| Git SHA (frontend RC build) | SHA-PLACEHOLDER |
| Frontend build artifact | DIST-ARTIFACT-REF-PLACEHOLDER |
| Branch / tag | BRANCH-PLACEHOLDER |
| Freeze timestamp (UTC) | FREEZE-UTC-PLACEHOLDER |
| Change request / ticket ID | TICKET-PLACEHOLDER |

---

## 2. Frontend RC gate verdict

| Field | Value |
|-------|--------|
| **Frontend RC gate verdict** | TBD |
| Artifact | \`frontend-rc-readiness-summary.md\` + \`frontend-rc-readiness-results.json\` |
| Environment skips | none / document in §6 |

| Check | Result | Notes |
|-------|--------|-------|
| check-no-secrets | TBD | |
| vitest-unit | TBD | test count: TBD |
| typescript-build | TBD | |
| playwright-smoke | TBD | scope: tests/e2e chromium |
| frontend-production-build | TBD | |

---

## 3. Route inventory summary

From RC JSON \`routeInventory\` (counts only — list in artifact):

| Bucket | Route count |
|--------|-------------|
| Auth | TBD |
| App shell / admin app routes | TBD |
| Workspace / notes | TBD |
| Search / settings | TBD |
| Admin marker routes | TBD |
| **Total** | TBD |

---

## 4. Domain status (after visual QA)

| Domain | Status | Notes |
|--------|--------|-------|
| User — Auth | ready / partial / blocked | |
| User — App shell / hub | ready / partial / blocked | |
| User — Note editor / collaboration | ready / partial / blocked | |
| User — Search / settings / offline sync | ready / partial / blocked | |
| Admin — Overview / audit / setup | ready / partial / blocked | |
| Admin — Identity / SSO / SCIM / RBAC diagnostics | ready / partial / blocked | |
| Admin — Break-glass / security | ready / partial / blocked | |
| Admin — Change requests / GitOps diff | ready / partial / blocked | |
| Admin — Retention / notification ops / legal holds | ready / partial / blocked | |

---

## 5. Responsive and accessibility baseline

| Area | Status | Reference |
|------|--------|-----------|
| Mobile viewport | pass / fail / waived | Visual QA § Viewports |
| Tablet viewport | pass / fail / waived | |
| Desktop viewport | pass / fail / waived | |
| Keyboard navigation | pass / fail / waived | Faz 145 baseline |
| Focus states (\`:focus-visible\`) | pass / fail / waived | |
| Dialog / modal behavior | pass / fail / waived | |
| No token/secret visible in UI | pass / fail | Global G1 |

Visual QA checklist completed: **no** (default)

---

## 6. Known limitations

| ID | Limitation | Blocks GO? |
|----|------------|------------|
| L1 | E2E smoke uses Playwright API stubs | no / yes |
| L2 | axe-core not in RC gate | no / yes |
| L3 | Legacy frontend/e2e/ not in RC gate | no / yes |
| L4 | Note editor E2E = route shell only | no / yes |
| L5 | Admin metrics require live admin APIs | no / yes |
| L6 | Search relevance not frontend-gated | no / yes |
| L7 | Playwright skipped on PR-only RC run | yes until full main run |

---

## 7. Accepted risks

| ID | Description | Owner | Expiry |
|----|-------------|-------|--------|
| AR-1 | PLACEHOLDER | OWNER-PLACEHOLDER | DATE-PLACEHOLDER |

---

## 8. Attached artifacts checklist

- [ ] \`frontend-rc-readiness-results.json\`
- [ ] \`frontend-rc-readiness-summary.md\`
- [ ] Completed [frontend-release-visual-qa-checklist.md](../docs/frontend-release-visual-qa-checklist.md)
- [ ] Production build reference (CI job / dist hash only)

---

## 9. Final decision

**Rules:** RC \`PASS\` + visual QA complete + no blocker → \`GO\`; RC \`PASS\` + accepted visual limitations → \`GO_WITH_ACCEPTED_RISKS\`; RC \`FAIL\`, Playwright fail, TypeScript fail, secret leak, or major route broken → \`NO_GO\`.

| Field | Value |
|-------|--------|
| **Final decision** | **NO_GO** |
| Rationale | Template default — replace after RC artifacts attached and visual QA completed. |

### Approvers

| Role | Name | Date (UTC) | Sign-off |
|------|------|------------|----------|
| Frontend / release owner | NAME-PLACEHOLDER | DATE-PLACEHOLDER | [ ] |
| QA (visual checklist) | NAME-PLACEHOLDER | DATE-PLACEHOLDER | [ ] |
| Accessibility (if waivers) | NAME-PLACEHOLDER | DATE-PLACEHOLDER | [ ] |

---

## 10. Production flags

**Production feature flags:** not enabled by this sign-off. Backend sign-off is separate.

EOF
}

if [[ -n "$OUTPUT" ]]; then
  emit >"$OUTPUT"
  echo "Wrote frontend sign-off template: $OUTPUT"
else
  emit
fi
