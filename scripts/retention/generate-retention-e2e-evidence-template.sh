#!/usr/bin/env bash
#
# Emit a blank sanitized change-request evidence template (Faz 114).
# No secrets, JDBC URLs, tokens, or PII — placeholders only.

set -euo pipefail

GENERATED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -u)"

cat <<EOF
# Staging dedicated retention datasource — evidence bundle (template)

Generated: ${GENERATED_AT}
Source: scripts/retention/generate-retention-e2e-evidence-template.sh

> Sanitized summary only. Do not paste credentials, raw actuator JSON, SQL output, or API bodies.

## Identity & deployment

| Field | Value |
|-------|--------|
| Change request ID | CR-PLACEHOLDER |
| Environment | staging |
| Git SHA / image tag | SHA-PLACEHOLDER |
| Argo CD sync revision | REVISION-PLACEHOLDER |
| Retention overlay commit | OVERLAY-COMMIT-PLACEHOLDER |
| ExternalSecret synced | yes / no |

## Preflight SQL (PASS/FAIL only)

| Domain | Result |
|--------|--------|
| content | pass / fail |
| notification | pass / fail |
| workspace | pass / fail |
| search | pass / fail |

## Actuator retention datasource health

| Service | lastCheckStatus | Dedicated pool | Warnings |
|---------|-----------------|------------------|----------|
| content (contentRetentionDataSourceHealth) | UP / DOWN / DISABLED / FALLBACK_PRIMARY | yes / no | none / SYMBOLIC-CODE |
| notification (notificationRetentionDataSourceHealth) | | | |
| workspace (workspaceRetentionDataSourceHealth) | | | |
| search (searchRetentionDataSourceHealth) | | | |

## Smoke summary

Attach CI artifact: retention-staging-smoke-evidence

| Domain | Status | Exit | Expect ready |
|--------|--------|------|--------------|
| content | passed / expected-gap / skipped | 0 | true |
| notification | | | true |
| workspace | | | true |
| search | | | true |

Overall: passed / failed / skipped (exit 0)

## Grafana platform-retention-readiness

| Service | Status | UNAVAILABLE | COUNT_CAPPED | Blocked-by-hold |
|---------|--------|-------------|--------------|-----------------|
| content-service | READY | none | none | none |
| notification-service | | | | |
| workspace-service | | | | |
| search-service | | | | |

## Rollback drill

| Item | Value |
|------|--------|
| Rollback tested | yes / no |
| Post-rollback actuator | DISABLED (all services) |
| Post-rollback smoke | expected-gap |

## Approval notes

-

EOF
