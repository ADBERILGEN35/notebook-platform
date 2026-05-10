# Fine-grained admin RBAC (Faz 79)

Enterprise deployments need **least privilege** and **separation of duties** on admin surfaces. This phase adds a permission model carried in **signed JWTs** from identity-service and enforced in api-gateway. The UI only reflects capabilities; the gateway remains authoritative.

## Feature flags

| Variable | Service | Default | Purpose |
|----------|---------|---------|---------|
| `ADMIN_RBAC_ENABLED` | identity-service | `false` | Emit `platform_permissions` from resolved roles; map IdP/SCIM groups to fine-grained roles. |
| `ADMIN_RBAC_LEGACY_PLATFORM_ADMIN_IMPLIES_ALL` | identity-service | `true` | `PLATFORM_ADMIN` resolves to the full permission set (compatibility). |
| `ADMIN_RBAC_GROUP_*` | identity-service | empty | Group name (single key, lowercase match) per role; see below. |
| `GATEWAY_ADMIN_RBAC_ENFORCE` | api-gateway | `false` | When `true`, admin routes require `platform_permissions` (or `PLATFORM_ADMIN` / legacy admin role in JWT), not email allowlist alone. |

**Rollout:** Keep `ADMIN_RBAC_ENABLED=false` and `GATEWAY_ADMIN_RBAC_ENFORCE=false` until IdP/SCIM groups are configured. Then enable identity RBAC first (tokens gain permissions), then flip gateway enforce in a controlled window.

## Roles and permissions

Canonical strings live in `common-security` (`PlatformAdminRbacConstants`).

**Legacy super-role**

- `PLATFORM_ADMIN` — when RBAC is enabled and legacy flag is true, implies **all** permissions (same as today operationally).

**Fine-grained roles**

- `PLATFORM_AUDIT_VIEWER`
- `PLATFORM_AUDIT_EXPORTER`
- `PLATFORM_SECURITY_ADMIN`
- `PLATFORM_IDENTITY_ADMIN`
- `PLATFORM_CHANGE_REQUEST_AUTHOR`
- `PLATFORM_CHANGE_REQUEST_APPROVER`
- `PLATFORM_OBSERVABILITY_VIEWER`

**Permission strings** (examples)

- Audit: `admin:audit:read`, `admin:audit:export`
- Enterprise console: `admin:enterprise:status:read`
- Notification delivery analytics (aggregate dashboard): `admin:notifications:analytics:read` (`PLATFORM_OBSERVABILITY_VIEWER` includes this; see Faz 81)
- Notification dead-letter (fanout DEAD rows): `admin:notifications:dead-letter:read`, `admin:notifications:dead-letter:requeue` (Faz 82; observability viewer = read-only)
- Notification retention (planner + optional purge): `admin:notifications:retention:read`, `admin:notifications:retention:run` (Faz 83; observability viewer = read-only; security admin = read + run)
- Notification legal holds (retention governance): `admin:notifications:legal-hold:read`, `admin:notifications:legal-hold:write` (Faz 84; observability viewer = read-only; security admin = read + write)
- **Workspace notification policies** (Faz 85) are **not** governed here: notification-service authorizes `PATCH` / `POST .../reset` on `/notification-policies/workspaces/{workspaceId}` using **workspace owner/admin** membership from workspace-service. Platform admin permissions alone do **not** grant workspace policy writes unless the user is also an owner/admin of that workspace.
- Change requests: `admin:change-request:list`, `:create`, `:approve`, `:reject`, `:cancel`
- Operation-specific creates: `admin:security:change-request:create`, `admin:merge:change-request:create`, `admin:scim:change-request:create`, `admin:siem:change-request:create`
- Identity read: `admin:identity:read`

Full matrix: [`docs/admin-permission-matrix.md`](admin-permission-matrix.md).

## JWT claims

Access tokens may include:

- `platform_roles` — string list (existing)
- `platform_permissions` — resolved permission list when `ADMIN_RBAC_ENABLED=true`

Clients must **not** send permission headers; gateway trusts only the signed JWT.

## Group mapping (SSO + SCIM)

When RBAC is enabled, identity-service maps **normalized lowercase** group identifiers to roles:

- SSO: groups from the IdP claim (`SSO_GROUPS_CLAIM`), plus legacy `SSO_ADMIN_GROUPS` still promoting `PLATFORM_ADMIN` when matched.
- SCIM: effective group keys from provisioning / nesting (`docs/scim-group-nesting.md`).

Environment keys (Helm: `config.adminRbacGroup*`):

- `ADMIN_RBAC_GROUP_PLATFORM_ADMIN`
- `ADMIN_RBAC_GROUP_AUDIT_VIEWER`
- `ADMIN_RBAC_GROUP_AUDIT_EXPORTER`
- `ADMIN_RBAC_GROUP_SECURITY_ADMIN`
- `ADMIN_RBAC_GROUP_IDENTITY_ADMIN`
- `ADMIN_RBAC_GROUP_CHANGE_REQUEST_AUTHOR`
- `ADMIN_RBAC_GROUP_CHANGE_REQUEST_APPROVER`
- `ADMIN_RBAC_GROUP_OBSERVABILITY_VIEWER`

Each value is a **single** group key to match (e.g. `notebook-audit-viewers`).

## Gateway behavior (`GATEWAY_ADMIN_RBAC_ENFORCE=true`)

- Endpoints require specific permissions (see matrix doc).
- **Email/user allowlist** does **not** substitute for missing `platform_permissions` / admin role.
- `PLATFORM_ADMIN` in JWT still bypasses permission checks.
- **MFA** rules (Faz 52 / 77–78) apply independently: sensitive write/approve paths still require step-up when configured.

## Internal status

Identity internal security status includes `adminRbac`: enabled flag, legacy flag, and which group mappings are non-empty. Gateway enterprise status aggregates this for the Admin Console card.

## Related documentation

- [`docs/admin-permission-matrix.md`](admin-permission-matrix.md)
- [`docs/enterprise-sso.md`](enterprise-sso.md)
- [`docs/scim-group-nesting.md`](scim-group-nesting.md)
- [`docs/enterprise-admin-console.md`](enterprise-admin-console.md)
- [`docs/admin-change-request-approval-workflow.md`](admin-change-request-approval-workflow.md)
- [`docs/admin-mfa-enforcement.md`](admin-mfa-enforcement.md)

## Troubleshooting

| Symptom | Check |
|---------|--------|
| 403 `ADMIN_PERMISSION_REQUIRED` | User JWT missing permission; verify IdP/SCIM group → `ADMIN_RBAC_GROUP_*` mapping and `ADMIN_RBAC_ENABLED=true`. |
| 403 after enabling enforce | Users need refreshed tokens with `platform_permissions`; allowlist-only no longer sufficient. |
| SSO admin works but no fine roles | Ensure `SSO_GROUPS_CLAIM` matches IdP; RBAC group envs must match **lowercased** group values. |
