# Break-glass runbook (Faz 90-91)

## Preconditions

- Confirm normal admin access is unavailable due to SSO/SCIM/RBAC/MFA/config misconfiguration.
- Ensure on-call security approver is present (out-of-band process).

## Emergency enablement (temporary)

1. Enable identity break-glass (temporary)
   - `BREAK_GLASS_ENABLED=true`
   - `BREAK_GLASS_CREDENTIAL_MODE=static-token|webauthn|offline-signed|hybrid`
   - `BREAK_GLASS_STATIC_TOKEN_ENABLED=false` (enable only if needed)
   - `BREAK_GLASS_WEBAUTHN_ENABLED=false` (enable only if provisioned)
   - `BREAK_GLASS_OFFLINE_SIGNED_ENABLED=false` (preferred emergency mode when prepared)
   - `BREAK_GLASS_TOKEN_HASH=sha256:<hex>` (from secret manager / external secret)
   - `BREAK_GLASS_OFFLINE_PUBLIC_KEY_PATH=/etc/notebook/secrets/break-glass/public.pem` (offline-signed mode)
   - `BREAK_GLASS_SESSION_TTL_MINUTES=15`
   - `BREAK_GLASS_MAX_ACTIVE_SESSIONS=1`

2. Enable gateway acceptance (temporary)
   - `GATEWAY_BREAK_GLASS_ADMIN_ALLOWED=true`
   - `GATEWAY_BREAK_GLASS_ALLOWED_MODES=webauthn,offline-signed` (prefer non-static)
   - Keep `BREAK_GLASS_ALLOW_ADMIN_WRITE=false` unless strictly required

3. Confirm readiness (no secrets)
   - Enterprise console → Security → “Break-glass admin access”
   - Or `GET /auth/break-glass/status` (identity-service via gateway route) to confirm posture.
   - If governance is enabled: verify approval mode and pending review counters.
   - If revocation is enabled: verify denylist check flags in gateway.

## Login

Use one of:
- `POST /auth/break-glass/login` (static token)
- `POST /auth/break-glass/webauthn/challenge` + `/webauthn/verify`
- `POST /auth/break-glass/offline-signed/login`

Always provide a detailed reason (>= 20 chars).

If `BREAK_GLASS_APPROVAL_MODE=required_before_issue`, workflow may return approval-required without issuing a token.

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
- Rotate static token and update `BREAK_GLASS_TOKEN_HASH` if static mode was used.
  When `BREAK_GLASS_STATIC_TOKEN_ROTATION_TRACKING_ENABLED=true`, follow the
  governance flow in [`break-glass-token-rotation-runbook.md`](break-glass-token-rotation-runbook.md):
  acknowledge → update External Secret → restart pods → verify → close.
- Review audit events:
  - `BREAK_GLASS_LOGIN_ATTEMPT`, `BREAK_GLASS_LOGIN_FAILED`, `BREAK_GLASS_LOGIN_SUCCEEDED`, `BREAK_GLASS_SESSION_ISSUED`
  - `BREAK_GLASS_EVENT_CREATED`, `BREAK_GLASS_REVIEW_*`
  - `BREAK_GLASS_TOKEN_REVOKED`, `BREAK_GLASS_TOKEN_ALREADY_*`
  - `BREAK_GLASS_TOKEN_ROTATION_REQUIRED|ACKNOWLEDGED|VERIFIED|CLOSED`
- Post-incident review: root cause, controls, and any policy improvements.

