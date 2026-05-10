# Enterprise Admin Console (Faz 63)

Read-only, **admin-only** visibility into enterprise security posture. No secrets, tokens, or private keys are exposed to the browser.

## User-facing surface

- Frontend routes (behind `ADMIN_UI_ENABLED` and platform-admin style roles):
  - `/app/admin/enterprise` — overview, readiness heuristic, warnings, documentation pointers
  - `/app/admin/enterprise/security` — SSO, SCIM, MFA, SIEM, audit export, gateway security
  - `/app/admin/enterprise/integrations` — notification SSE/digest status
  - `/app/admin/enterprise/change-requests` — **Faz 77–78**: validated change requests, optional **approve/reject** UI (`FRONTEND_ENTERPRISE_ADMIN_WRITE_ENABLED`, optional `FRONTEND_ENTERPRISE_ADMIN_APPROVALS_ENABLED`)
  - `/app/admin/notifications/analytics` — **Faz 81**: privacy-safe notification delivery aggregates (`FRONTEND_NOTIFICATION_ANALYTICS_ENABLED`, permission `admin:notifications:analytics:read`)
  - `/app/admin/notifications/dead-letter` — **Faz 82**: fanout `DEAD` rows, dry-run + requeue (`FRONTEND_NOTIFICATION_DEAD_LETTER_ENABLED`; read/requeue permissions)
  - `/app/admin/notifications/retention` — **Faz 83**: retention plan / dry-run; optional destructive purge UI (`FRONTEND_NOTIFICATION_RETENTION_ENABLED`, `FRONTEND_NOTIFICATION_RETENTION_PURGE_ENABLED`; read/run permissions)
  - `/app/admin/notifications/legal-holds` — **Faz 84**: notification legal holds (`FRONTEND_NOTIFICATION_LEGAL_HOLD_ENABLED`; read/write permissions; write MFA when enforced)
- Gateway API: `GET /admin/enterprise/status` (JSON); **Faz 77–78** write surface: `/admin/enterprise/change-requests` including approve/reject (see `docs/enterprise-admin-write-operations.md`, `docs/admin-change-request-approval-workflow.md`); **Faz 81** `GET /admin/notifications/analytics/summary` (see `docs/notification-analytics-dashboard.md`); **Faz 82** `/admin/notifications/dead-letter` (see `docs/notification-dead-letter-requeue.md`); **Faz 83** `/admin/notifications/retention/plan`, `POST /admin/notifications/retention/run` (see `docs/notification-retention-worker.md`); **Faz 84** `/admin/notifications/legal-holds` (see `docs/notification-legal-hold.md`)

## Authorization

- Requires authenticated user JWT (bearer or cookie transport, same as other admin routes).
- Platform admin allowlist / `PLATFORM_ADMIN` style claims (see gateway admin configuration).
- **Faz 79:** When `GATEWAY_ADMIN_RBAC_ENFORCE=true`, routes require `platform_permissions` from identity (or `PLATFORM_ADMIN`). Allowlist-only users are not granted admin APIs. See [`docs/admin-rbac.md`](admin-rbac.md).
- **Admin MFA**: When gateway admin MFA policy blocks the user, responses use `ADMIN_MFA_REQUIRED` (HTTP 403), consistent with the audit UI (Faz 52).

## Architecture

1. **Browser** calls only the gateway: `/admin/enterprise/status`.
2. **Gateway** validates admin auth, then uses the **same service JWT signer** as the audit proxy to call:
   - `GET /internal/admin/status/identity-security` on **identity-service** (scope `internal:admin:status:read`, audience `identity-service`).
   - `GET /internal/admin/status/notification` on **notification-service** (same scope, audience `notification-service`).
   - `GET /internal/admin/status/content` on **content-service** (same scope, audience `content-service`).
3. **Gateway** merges:
   - Identity fields (SSO, SCIM, identity MFA/WebAuthn flags, SIEM booleans).
   - Notification fields (SSE, digest, email provider class — no API keys).
   - Content merge fields (analyze/apply flags, merge versions, idempotency/metrics/audit-failure toggles).
   - Local gateway fields (admin/audit flags, MFA mode, audit export + machine-auth flags, rate limit/CSRF/transport summary).
4. **Warning engine** adds `INFO` / `WARNING` / `CRITICAL` items (e.g. SSO without admin mapping, SCIM without token, SIEM worker off).

### Partial status

If an internal service is down, returns **HTTP 200** with `identityUnavailable` / `notificationUnavailable` / `contentUnavailable` and matching warnings — the page still renders with safe defaults for missing slices.

## Feature flags

| Flag | Service | Purpose |
|------|---------|---------|
| `GATEWAY_ADMIN_ENTERPRISE_ENABLED` | api-gateway | Enables `/admin/enterprise/status`. |
| `IDENTITY_INTERNAL_ADMIN_STATUS_ENABLED` | identity-service | Enables `/internal/admin/status/identity-security` (404 when false). |
| `NOTIFICATION_INTERNAL_ADMIN_STATUS_ENABLED` | notification-service | Enables `/internal/admin/status/notification` (404 when false). |
| `CONTENT_INTERNAL_ADMIN_STATUS_ENABLED` | content-service | Enables `/internal/admin/status/content` (404 when false). |
| `NOTIFICATION_INTERNAL_ADMIN_ANALYTICS_ENABLED` | notification-service | Enables `/internal/admin/notifications/analytics/summary` (404 when false). |
| `NOTIFICATION_DEAD_LETTER_ADMIN_ENABLED` | notification-service | Enables `/internal/admin/notifications/dead-letter` (404 when false). |
| `NOTIFICATION_RETENTION_ADMIN_API_ENABLED` | notification-service | Enables `/internal/admin/notifications/retention` (404 when false). |
| `NOTIFICATION_LEGAL_HOLD_ENABLED` | notification-service | When true, active holds block matching retention purges. |
| `NOTIFICATION_LEGAL_HOLD_ADMIN_API_ENABLED` | notification-service | Enables `/internal/admin/notifications/legal-holds` (404 when false). |
| `FRONTEND_NOTIFICATION_ANALYTICS_ENABLED` | frontend | Injects dashboard feature flag at container start. |
| `FRONTEND_NOTIFICATION_DEAD_LETTER_ENABLED` | frontend | Injects dead-letter UI flag at container start. |
| `FRONTEND_NOTIFICATION_RETENTION_ENABLED` | frontend | Injects retention UI flag at container start. |
| `FRONTEND_NOTIFICATION_RETENTION_PURGE_ENABLED` | frontend | Shows destructive purge modal (server still enforces manual run + MFA). |
| `FRONTEND_NOTIFICATION_LEGAL_HOLD_ENABLED` | frontend | Shows legal holds admin page (server still enforces RBAC + MFA on writes). |

Identity and notification **verify** gateway service JWTs using existing audit-admin trust material; notification adds `trusted-gateway-admin-client` with scope allow-list including `internal:admin:status:read` and **Faz 81** `internal:admin:notifications:analytics:read` for analytics proxy calls, plus **Faz 84** legal-hold read/write scopes when enabled in deployment config.

## Out of scope (Faz 63)

- Editing SSO/SCIM/SIEM configuration from UI.
- Secret rotation or token display.
- Full org / billing console.

## Related docs

- `docs/enterprise-admin-write-operations.md`, `docs/admin-change-request-workflow.md`
- `docs/admin-audit-ui.md`, `docs/admin-audit-proxy.md`
- `docs/enterprise-sso.md`, `docs/scim-provisioning.md`, `docs/siem-streaming-push.md`
- `docs/notification-service.md`, `docs/security-threat-model.md`, `docs/production-readiness.md`
