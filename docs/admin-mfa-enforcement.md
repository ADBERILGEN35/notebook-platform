# Admin MFA Enforcement

## Scope

Admin MFA enforcement applies to gateway admin endpoints under `/admin/**`, including:

- `/admin/audit-events`
- `/admin/audit-events/export`
- `/admin/enterprise/status`
- **Faz 77–78:** `/admin/enterprise/change-requests` **writes** (validate, create, cancel, **approve**, **reject**) use the same policy: any non-`off` MFA mode (or `require-mfa`) requires satisfied MFA for **platform-admin role** callers (`403 ADMIN_WRITE_MFA_REQUIRED` when blocked). See `docs/enterprise-admin-write-operations.md`.

## Gateway Config

- `gateway.admin.require-mfa` (default `false`, backward-compatible)
- `gateway.admin.mfa-mode` (`off|observe|warn|enforce`, default `off`)
- `gateway.admin.mfa-accepted-methods` (default `webauthn,recovery_code`)

`require-mfa=true` or `mfa-mode=enforce` activates blocking behavior.

## Enforcement Rules

For admin-authorized users:

- Missing `mfa_verified=true` and no accepted `amr` method -> `403 ADMIN_MFA_REQUIRED`
- `amr` present but no accepted method -> `403 ADMIN_MFA_REQUIRED`

Accepted methods are matched from access token `amr` claim against
`gateway.admin.mfa-accepted-methods`.

**Faz 79:** Fine-grained `platform_permissions` are independent of MFA: a user may hold audit or approver permissions but still be blocked by MFA policy on protected admin routes when step-up is required.

## Frontend Behavior

Admin audit UI maps `ADMIN_MFA_REQUIRED` to a dedicated state:

- Message: `Admin access requires multi-factor authentication.`
- CTA: `Go to Security Settings` (navigates to `/app/settings`)

## Rollout Plan

Recommended rollout:

1. `off` in dev by default
2. `observe` in staging while tracking token claim coverage
3. `warn` in staging for operational rehearsal
4. `enforce` in production after admin passkey coverage is sufficient

Current environment baseline (Faz 53 cleanup):

- dev: `off`
- staging: `warn`
- prod: `observe` (then `warn`/`enforce` after readiness review)

## Monitoring

Gateway emits structured warning logs when blocking:

- event key: `admin_mfa_required_blocked`
- fields: `adminUserId`, `endpoint`, `requestId`, `amr`

## SSO admin identities (Faz 60 + Faz 79)

- Admin identity may come from:
  - `platform_roles` token claim (preferred)
  - `platform_permissions` when `ADMIN_RBAC_ENABLED=true`
  - legacy `roles`
  - allowlist fallback (gateway legacy mode; not a substitute for permissions when `GATEWAY_ADMIN_RBAC_ENFORCE=true`)
- SSO / SCIM group-to-role mapping happens in identity-service, not in gateway. See [`docs/admin-rbac.md`](admin-rbac.md).

