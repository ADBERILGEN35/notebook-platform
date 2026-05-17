#!/usr/bin/env bash
#
# Emit a blank provider certification checklist (markdown) for change requests (Faz 120).
# Usage: generate-scim-delta-certification-evidence-template.sh [okta|entra|generic]

set -euo pipefail

PROVIDER="${1:-generic}"
GENERATED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -u)"

case "$PROVIDER" in
  okta)
    PROVIDER_NAME="Okta"
    PROVIDER_TYPE="okta"
    STRATEGY="LAST_MODIFIED_FILTER"
    ;;
  entra|azure-ad|azure_ad)
    PROVIDER_NAME="Microsoft Entra ID"
    PROVIDER_TYPE="azure-ad"
    STRATEGY="CURSOR_CHECKPOINT"
    ;;
  generic)
    PROVIDER_NAME="Generic SCIM 2.0"
    PROVIDER_TYPE="generic"
    STRATEGY="FULL_SYNC_FALLBACK"
    ;;
  *)
    echo "unknown provider: $PROVIDER (use okta, entra, or generic)" >&2
    exit 1
    ;;
esac

cat <<EOF
# SCIM Delta Provider Certification — ${PROVIDER_NAME}

Generated: ${GENERATED_AT}
Template: scripts/scim/generate-scim-delta-certification-evidence-template.sh
Reference: docs/scim-delta-provider-certification.md

> Attach sanitized \`scim-delta-remote-fetch-evidence.json\` only. No tokens, raw SCIM payloads, or cursor values.

## Run metadata

| Field | Value |
|-------|--------|
| Provider name | ${PROVIDER_NAME} |
| Provider type | ${PROVIDER_TYPE} |
| Sandbox tenant/environment | SANDBOX-PLACEHOLDER |
| Test date | DATE-PLACEHOLDER |
| Tested by | NAME-PLACEHOLDER |
| App version / Git SHA | SHA-PLACEHOLDER |
| Change request ID | CR-PLACEHOLDER |
| Strategy selected (expected) | ${STRATEGY} |
| Remote fetch enabled | yes / no |
| Multi-page enabled | yes / no |
| SCIM_DELTA_REMOTE_MAX_PAGES | |
| SCIM_DELTA_REMOTE_MAX_RESOURCES | |

## Dry-run evidence summary (from JSON)

| Field | Value |
|-------|--------|
| evidenceStatus | passed / skipped / failed |
| remoteFetchConfigured | |
| remoteFetchAttempted | |
| pagesObserved | |
| fetchedResourceCount | |
| stoppedReason | |
| nextCursorPresent | |
| providerErrorClass | |
| retryAfterSeconds | n/a |
| pageLimitReached | |
| resourceLimitReached | |
| warnings | SCIM_DELTA_* codes only |

## Acceptance criteria

| ID | Criterion | Result (pass/fail/N/A) | Notes |
|----|-----------|------------------------|-------|
| C1 | Auth / config success | | secretKeyRef attestation |
| C2 | One-page GET success | | GET-only |
| C3 | Multi-page observed or valid stop | | |
| C4 | Rate-limit (429) tested or documented N/A | | |
| C5 | Retry-After tested or documented N/A | | |
| C6 | Timeout / bad response tested or documented N/A | | |
| C7 | Missing-from-delta does not deprovision | | deprovisionedCount=0 |
| C8 | active=false semantics documented | | scim-provisioning.md |
| C9 | DELETE semantics documented | | |
| C10 | externalId behavior documented | | |
| C11 | Privacy — validate-scim-delta-evidence.sh PASS | | |

## Semantic attestation (documentation)

- Missing from delta page does **not** trigger deprovision.
- Deprovision only on explicit \`active=false\` or DELETE event.
- externalId is preferred identity key; email conflicts are manual review.
- No production scheduler enabled for this CR.

## Blockers observed

- [ ] None
- [ ] Token / Authorization leak
- [ ] Raw SCIM body in evidence
- [ ] Non-GET HTTP used
- [ ] Deprovision from missing delta
- [ ] Raw cursor / next URL leak
- [ ] Other: 

## Final result

**certified / blocked / needs review**

Approver signature / date:

EOF
