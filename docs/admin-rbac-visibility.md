# Admin RBAC visibility (Faz 86)

Identity exposes **read-only** admin RBAC directory APIs for the Enterprise Admin Console. Responses include effective platform roles, resolved permissions (when fine-grained RBAC is enabled), and **source summaries** (`SSO_GROUP`, `SCIM_GROUP`, `LEGACY_PLATFORM_ADMIN`, `ALLOWLIST` mirror, and **`GITOPS_OVERRIDE`** when Faz 88 ingestion is enabled and a row applies). Source entries may include `reasonRef` (e.g. `change-request:<uuid>`) for traceability. Raw IdP claims, SCIM tokens, nested group graphs, and **raw override YAML** are **not** returned.

## Flags

| Component | Flag | Default |
|-----------|------|---------|
| identity-service | `ADMIN_RBAC_VISIBILITY_ENABLED` | `false` |
| identity-service | `ADMIN_RBAC_ROLE_CHANGE_REQUESTS_ENABLED` | `false` |
| identity-service | `ADMIN_RBAC_VISIBILITY_ALLOWLIST_EMAILS` | empty (optional mirror for `ALLOWLIST` source hint) |
| api-gateway | `GATEWAY_ADMIN_RBAC_VISIBILITY_PATH` | `/internal/admin/rbac` |
| frontend (runtime) | `ADMIN_RBAC_UI_ENABLED` / `ADMIN_RBAC_ROLE_REQUESTS_ENABLED` (from `FRONTEND_ADMIN_RBAC_*` env) | `false` |
| frontend (runtime) | `ADMIN_RBAC_OVERRIDES_STATUS_ENABLED` ← `FRONTEND_ADMIN_RBAC_OVERRIDES_STATUS_ENABLED` | `false` (read-only “GitOps RBAC overrides” card on `/app/admin/rbac`) |

Service JWT scope for identity internal API: `internal:admin:rbac:read`.

## Privacy

- No raw IdP claim payloads in list/detail JSON.
- SCIM: effective membership drives role mapping; only **group display / external id** labels appear as `sourceName`.
- Email is shown (admin identity view).

## Audit

- `ADMIN_RBAC_USERS_VIEWED`, `ADMIN_RBAC_USER_DETAIL_VIEWED`
- RBAC change-request validate/create: `ADMIN_RBAC_ROLE_CHANGE_REQUEST_VALIDATED`, `ADMIN_RBAC_ROLE_CHANGE_REQUEST_CREATED`
- Overrides status/validate (internal, proxied by gateway with `admin:rbac:read`): `ADMIN_RBAC_OVERRIDES_STATUS_VIEWED`, `ADMIN_RBAC_OVERRIDES_VALIDATED`
- Overrides file load (identity startup): `ADMIN_RBAC_OVERRIDES_LOADED`, `ADMIN_RBAC_OVERRIDES_LOAD_FAILED` (metadata excludes raw YAML)

See also [`docs/admin-role-management-ui.md`](admin-role-management-ui.md), [`docs/admin-rbac-runtime-overrides.md`](admin-rbac-runtime-overrides.md).
