#!/usr/bin/env bash
#
# Emit a blank sanitized SCIM delta remote fetch evidence template (Faz 118).
# No tokens, raw SCIM payloads, Authorization headers, or PII.

set -euo pipefail

GENERATED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -u)"

cat <<EOF
# SCIM delta remote fetch — sandbox evidence bundle (template)

Generated: ${GENERATED_AT}
Source: scripts/scim/generate-scim-delta-evidence-template.sh

> Sanitized summary only. Do not paste bearer tokens, raw SCIM JSON, Authorization headers, or IdP PII.

## Deployment wiring

| Field | Value |
|-------|--------|
| Change request ID | CR-PLACEHOLDER |
| Environment | staging / dev |
| Git SHA / image tag | SHA-PLACEHOLDER |
| Overlay applied | okta / entra / generic example |
| SCIM_DELTA_REMOTE_FETCH_ENABLED | true / false |
| SCIM_DELTA_REMOTE_BASE_URL | https://<sandbox-host>/scim/v2 (no token) |
| Bearer via secretKeyRef | yes / no |
| ExternalSecret synced | yes / no / n/a |

## Manual dry-run (sanitized)

| Field | Value |
|-------|--------|
| providerType | okta / azure-ad / generic |
| selectedStrategy | PLACEHOLDER |
| remoteFetchEnabled | true / false |
| remoteFetchConfigured | true / false |
| remoteFetchAttempted | true / false |
| dryRunOnly | true |
| fetchedResourceCount | 0 |
| pageObserved | 0 / 1 |
| nextCursorPresent | true / false |
| providerErrorClass | NONE / RATE_LIMITED / ... |
| retryAfterSeconds | n/a |
| warnings | SCIM_DELTA_* codes only |

## Privacy attestation

- [ ] No raw SCIM list response attached
- [ ] No bearer token in ticket or logs
- [ ] No Authorization header captured
- [ ] deprovisionedCount remained 0

## Approval notes

-

EOF
