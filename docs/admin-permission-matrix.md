# Admin permission matrix (Faz 79–88)

Gateway routes and change-request operations require permissions when `GATEWAY_ADMIN_RBAC_ENFORCE=true`. When enforce is **false**, legacy admin checks apply (`PLATFORM_ADMIN`, allowlist, MFA).

## Role → permission mapping (identity-service)

Resolved when `ADMIN_RBAC_ENABLED=true`. `PLATFORM_ADMIN` adds **all** permissions if `ADMIN_RBAC_LEGACY_PLATFORM_ADMIN_IMPLIES_ALL=true`.

| Role | Permissions |
|------|-------------|
| `PLATFORM_ADMIN` | All permissions in `PlatformAdminRbacConstants.allPermissions()` |
| `PLATFORM_AUDIT_VIEWER` | `admin:audit:read` |
| `PLATFORM_AUDIT_EXPORTER` | `admin:audit:read`, `admin:audit:export` |
| `PLATFORM_SECURITY_ADMIN` | `admin:enterprise:status:read`, `admin:rbac:read`, `admin:change-request:list`, `admin:change-request:create`, `admin:security:change-request:create`, `admin:change-request:gitops:dry-run`, `admin:change-request:gitops:create`, `admin:notifications:dead-letter:read`, `admin:notifications:dead-letter:requeue`, `admin:notifications:retention:read`, `admin:notifications:retention:run`, `admin:notifications:legal-hold:read`, `admin:notifications:legal-hold:write` |
| `PLATFORM_IDENTITY_ADMIN` | `admin:identity:read`, `admin:enterprise:status:read`, `admin:rbac:read`, `admin:rbac:change-request:create`, `admin:change-request:list`, `admin:change-request:create`, `admin:scim:change-request:create`, `admin:change-request:gitops:dry-run`, `admin:change-request:gitops:create` |
| `PLATFORM_CHANGE_REQUEST_AUTHOR` | `admin:change-request:list`, `admin:change-request:create`, `admin:change-request:cancel` |
| `PLATFORM_CHANGE_REQUEST_APPROVER` | `admin:enterprise:status:read`, `admin:change-request:list`, `admin:change-request:approve`, `admin:change-request:reject`, `admin:change-request:gitops:dry-run`, `admin:change-request:gitops:create` |
| `PLATFORM_OBSERVABILITY_VIEWER` | `admin:enterprise:status:read`, `admin:notifications:analytics:read`, `admin:notifications:dead-letter:read`, `admin:notifications:retention:read`, `admin:notifications:legal-hold:read` |

## Gateway route → permission

| Route | Permission(s) |
|-------|----------------|
| `GET /admin/enterprise/status` | `admin:enterprise:status:read` |
| `GET /admin/rbac/users` | `admin:rbac:read` |
| `GET /admin/rbac/users/{userId}` | `admin:rbac:read` |
| `GET /admin/rbac/overrides/status` | `admin:rbac:read` |
| `POST /admin/rbac/overrides/reload` | `admin:rbac:override:reload` (+ admin-write MFA when configured) |
| `POST /admin/rbac/overrides/validate` | `admin:rbac:read` (body is YAML-in-JSON for dry-run; **no persistence**) |
| `GET /admin/notifications/analytics/summary` | `admin:notifications:analytics:read` |
| `GET /admin/notifications/dead-letter` | `admin:notifications:dead-letter:read` |
| `POST /admin/notifications/dead-letter/{id}/requeue/dry-run` | `admin:notifications:dead-letter:read` |
| `POST /admin/notifications/dead-letter/{id}/requeue` | `admin:notifications:dead-letter:requeue` + admin-write MFA when required |
| `GET /admin/notifications/retention/plan` | `admin:notifications:retention:read` |
| `POST /admin/notifications/retention/run` | `dryRun=true`: `admin:notifications:retention:read`; `dryRun=false`: `admin:notifications:retention:run` + admin-write MFA when required |
| `GET /admin/notifications/legal-holds` | `admin:notifications:legal-hold:read` |
| `POST /admin/notifications/legal-holds` | `admin:notifications:legal-hold:write` + admin-write MFA when required |
| `POST /admin/notifications/legal-holds/{id}/release` | `admin:notifications:legal-hold:write` + admin-write MFA when required |
| `GET /admin/audit-events` | `admin:audit:read` |
| `GET /admin/audit-events/export` | `admin:audit:export` |
| `GET /admin/enterprise/change-requests` | `admin:change-request:list` |
| `POST /admin/enterprise/change-requests/validate` | `admin:change-request:create` + operation permission |
| `POST /admin/enterprise/change-requests` | `admin:change-request:create` + operation permission |
| `POST /admin/enterprise/change-requests/{id}/approve` | `admin:change-request:approve` |
| `POST /admin/enterprise/change-requests/{id}/reject` | `admin:change-request:reject` |
| `POST /admin/enterprise/change-requests/{id}/cancel` | `admin:change-request:cancel` **or** `admin:change-request:create` (own pending); global cancel uses cancel permission via backend |
| `POST /admin/enterprise/change-requests/{id}/gitops/dry-run` | `admin:change-request:gitops:dry-run` |
| `POST /admin/enterprise/change-requests/{id}/gitops/create-pr` | `admin:change-request:gitops:create` + admin-write MFA when required |

## Change-request operation → extra create permission

| Operation type | Required (in addition to `admin:change-request:create`) |
|----------------|---------------------------------------------------------|
| `ADMIN_MFA_MODE_UPDATE` | `admin:security:change-request:create` |
| `MERGE_ANALYSIS_ROLLOUT_REQUEST` | `admin:merge:change-request:create` |
| `MERGE_APPLY_ROLLOUT_REQUEST` | `admin:merge:change-request:create` |
| `SCIM_BULK_ROLLOUT_REQUEST` | `admin:scim:change-request:create` |
| `ADMIN_RBAC_ROLE_GRANT_REQUEST` | `admin:rbac:change-request:create` |
| `ADMIN_RBAC_ROLE_REVOKE_REQUEST` | `admin:rbac:change-request:create` |

Identity `AdminOperationRegistry` mirrors these for validation. RBAC role requests use free-form `requestedValue` encoding (`grant|revoke:role:userId`); **no direct runtime mutation API** — approved rows land in GitOps (`admin-rbac-overrides.yaml`). **Faz 88:** identity-service may optionally merge **mounted** manifest rows into effective `platform_roles` when `ADMIN_RBAC_OVERRIDES_ENABLED=true` (default `false`); see [`docs/admin-rbac-runtime-overrides.md`](admin-rbac-runtime-overrides.md).

## Error codes

- `ADMIN_PERMISSION_REQUIRED` — missing permission; `details.permission` may be set.
- `ADMIN_OPERATION_PERMISSION_REQUIRED` — missing operation-specific create permission.

See [`docs/error-codes.md`](error-codes.md).
