# Break-glass runbook (Faz 90)

## Preconditions

- Confirm normal admin access is unavailable due to SSO/SCIM/RBAC/MFA/config misconfiguration.
- Ensure on-call security approver is present (out-of-band process).

## Emergency enablement (temporary)

1. Enable identity break-glass (temporary)
   - `BREAK_GLASS_ENABLED=true`
   - `BREAK_GLASS_TOKEN_HASH=sha256:<hex>` (from secret manager / external secret)
   - `BREAK_GLASS_SESSION_TTL_MINUTES=15`
   - `BREAK_GLASS_MAX_ACTIVE_SESSIONS=1`

2. Enable gateway acceptance (temporary)
   - `GATEWAY_BREAK_GLASS_ADMIN_ALLOWED=true`
   - Keep `BREAK_GLASS_ALLOW_ADMIN_WRITE=false` unless strictly required

3. Confirm readiness (no secrets)
   - Enterprise console → Security → “Break-glass admin access”
   - Or `GET /auth/break-glass/status` (identity-service via gateway route) to confirm posture.

## Login

Call `POST /auth/break-glass/login` with the offline token and a detailed reason (≥ 20 chars).

## Recovery actions (minimal)

- Prefer **read-only** operations first: enterprise status, audit logs, RBAC visibility.
- If changes are needed:
  - Prefer change requests / GitOps and safe reload patterns.
  - Avoid destructive writes; if unavoidable, temporarily set `BREAK_GLASS_ALLOW_ADMIN_WRITE=true` and revert immediately after the single operation.

## After recovery (mandatory)

- Disable break-glass everywhere:
  - `BREAK_GLASS_ENABLED=false`
  - `GATEWAY_BREAK_GLASS_ADMIN_ALLOWED=false`
  - `BREAK_GLASS_ALLOW_ADMIN_WRITE=false`
- Rotate the offline token and update `BREAK_GLASS_TOKEN_HASH`.
- Review audit events:
  - `BREAK_GLASS_LOGIN_ATTEMPT`, `BREAK_GLASS_LOGIN_FAILED`, `BREAK_GLASS_LOGIN_SUCCEEDED`, `BREAK_GLASS_SESSION_ISSUED`
- Post-incident review: root cause, controls, and any policy improvements.

