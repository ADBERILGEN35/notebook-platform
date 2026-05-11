# Admin role management UI (Faz 86)

The **Admin RBAC** page (`/app/admin/rbac`) lists users with effective platform roles and permission counts, source breakdown, and warnings (e.g. deprovisioned, broad `PLATFORM_ADMIN`, allowlist mirror hint).

## Role change requests (no direct mutation)

Operators may open **Request role grant** / **Request role revoke** when:

- `FRONTEND_ADMIN_RBAC_ROLE_REQUESTS_ENABLED=true`
- Enterprise change requests are enabled on the gateway
- JWT includes `admin:rbac:change-request:create` (and base `admin:change-request:create`)

Creates `ADMIN_RBAC_ROLE_GRANT_REQUEST` or `ADMIN_RBAC_ROLE_REVOKE_REQUEST` with `structuredPayload` (`userId`, `role`, `action`, `reason`). `PLATFORM_ADMIN` changes are **HIGH** severity and require `confirmation: "CONFIRM"`. Identity does **not** apply roles at runtime; approved requests follow existing GitOps/runbook workflows.

## GitOps overrides status (Faz 88, read-only)

When `FRONTEND_ADMIN_RBAC_OVERRIDES_STATUS_ENABLED=true` and the viewer has `admin:rbac:read`, the RBAC page shows a **GitOps RBAC overrides** card: ingestion enabled flag, loaded state, basename of configured file path, assignment counts, warnings, and last load time. There is **no** YAML upload, **no** editor, and **no** role mutation. User detail may list a `GITOPS_OVERRIDE` source with optional link from `reasonRef` to the originating change request.

## Related docs

- [`docs/admin-rbac-visibility.md`](admin-rbac-visibility.md)
- [`docs/admin-rbac-runtime-overrides.md`](admin-rbac-runtime-overrides.md)
- [`docs/admin-change-request-workflow.md`](admin-change-request-workflow.md)
- [`docs/admin-permission-matrix.md`](admin-permission-matrix.md)
