# Platform Retention Governance (Faz 98)

Faz 98 platform-wide retention/legal-hold foundation ekler. Production destructive deletion rollout yoktur.

## Flags

| Component | Flag | Default |
|-----------|------|---------|
| identity-service / gateway | `PLATFORM_RETENTION_GOVERNANCE_ENABLED` | `false` |
| identity-service / gateway | `PLATFORM_LEGAL_HOLD_ENABLED` | `false` |
| frontend | `FRONTEND_PLATFORM_RETENTION_GOVERNANCE_ENABLED` | `false` |

## APIs

Gateway:

- `GET /admin/retention/platform/targets`
- `GET /admin/retention/platform/plan`
- `GET /admin/retention/platform/legal-holds`
- `POST /admin/retention/platform/legal-holds`
- `POST /admin/retention/platform/legal-holds/{id}/release`

Identity internal:

- `GET /internal/admin/retention/platform/targets`
- `GET /internal/admin/retention/platform/plan`
- `GET /internal/admin/retention/platform/legal-holds`
- `POST /internal/admin/retention/platform/legal-holds`
- `POST /internal/admin/retention/platform/legal-holds/{id}/release`

## Permissions

- Read: `admin:retention:read`
- Legal hold create/release: `admin:retention:legal-hold:write` plus admin-write MFA when enforced
- Internal service JWT scopes:
  - `internal:admin:retention:platform:read`
  - `internal:admin:retention:legal-hold:write`

## Dry-run planner

The platform plan returns registry targets and hold blocking state. Faz 98 does not perform expensive cross-service row counts; unknown counts are returned as `null` / "Not counted" in the UI.

Destructive purge is not implemented. `dryRun=false` is ignored by the platform planner and returns a warning.

## Audit events

- `PLATFORM_RETENTION_TARGETS_VIEWED`
- `PLATFORM_RETENTION_PLAN_GENERATED`
- `PLATFORM_LEGAL_HOLD_CREATED`
- `PLATFORM_LEGAL_HOLD_RELEASED`
- `PLATFORM_LEGAL_HOLD_CREATE_DENIED`
- `PLATFORM_LEGAL_HOLD_RELEASE_DENIED`

## Metrics

- `platform_retention_plan_generated_total`
- `platform_legal_holds_active{scope}`
- `platform_retention_targets_total{status,riskLevel}`
- `platform_retention_blocked_by_hold_total{targetKey}`

Cardinality is bounded by registry target keys and legal-hold scopes. No workspaceId, userId, email, note id, or raw content labels.
