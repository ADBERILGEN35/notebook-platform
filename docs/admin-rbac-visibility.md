# Admin RBAC visibility (Faz 86)

Identity exposes **read-only** admin RBAC directory APIs for the Enterprise Admin Console. Responses include effective platform roles, resolved permissions (when fine-grained RBAC is enabled), and **source summaries** (`SSO_GROUP`, `SCIM_GROUP`, `LEGACY_PLATFORM_ADMIN`, `ALLOWLIST` mirror). Raw IdP claims, SCIM tokens, and nested group graphs are **not** returned.

## Flags

| Component | Flag | Default |
|-----------|------|---------|
| identity-service | `ADMIN_RBAC_VISIBILITY_ENABLED` | `false` |
| identity-service | `ADMIN_RBAC_ROLE_CHANGE_REQUESTS_ENABLED` | `false` |
| identity-service | `ADMIN_RBAC_VISIBILITY_ALLOWLIST_EMAILS` | empty (optional mirror for `ALLOWLIST` source hint) |
| api-gateway | `GATEWAY_ADMIN_RBAC_VISIBILITY_PATH` | `/internal/admin/rbac` |
| frontend (runtime) | `ADMIN_RBAC_UI_ENABLED` / `ADMIN_RBAC_ROLE_REQUESTS_ENABLED` (from `FRONTEND_ADMIN_RBAC_*` env) | `false` |

Service JWT scope for identity internal API: `internal:admin:rbac:read`.

## Privacy

- No raw IdP claim payloads in list/detail JSON.
- SCIM: effective membership drives role mapping; only **group display / external id** labels appear as `sourceName`.
- Email is shown (admin identity view).

## Audit

- `ADMIN_RBAC_USERS_VIEWED`, `ADMIN_RBAC_USER_DETAIL_VIEWED`
- RBAC change-request validate/create: `ADMIN_RBAC_ROLE_CHANGE_REQUEST_VALIDATED`, `ADMIN_RBAC_ROLE_CHANGE_REQUEST_CREATED`

See also [`docs/admin-role-management-ui.md`](admin-role-management-ui.md).
