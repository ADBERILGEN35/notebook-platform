# Break-glass admin access (Faz 90-93)

## Summary

Break-glass is an **emergency-only** access path to prevent total platform lockout when SSO/SCIM/RBAC/MFA/configuration is misconfigured.

**This capability is disabled by default** across identity-service, api-gateway, and frontend.

## Threat model (high level)

### Assets
- Admin privileges (ability to read/write admin surfaces)
- Audit integrity
- Service JWT trust boundaries

### Adversaries / failure modes
- Token leakage (operator error, logs, CI artifacts)
- Insider abuse
- Misconfiguration enabling break-glass permanently
- Gateway accepting break-glass tokens unintentionally

### Guardrails implemented in Faz 90-93
- **Disabled by default** (`BREAK_GLASS_ENABLED=false`, `GATEWAY_BREAK_GLASS_ADMIN_ALLOWED=false`)
- **Short-lived access token only** (no refresh token)
- **Reason required** (minimum 20 chars; server validated)
- **No secret exposure** (no token/hash returned in status, no raw secret in logs)
- **Gateway write blocked by default** (`BREAK_GLASS_ALLOW_ADMIN_WRITE=false`)
- **Audit events** for attempt/success/failure/session issued (identity-service)
- **Credential mode controls** (`static-token|webauthn|offline-signed|hybrid`)
- **Gateway mode allow-list** (`GATEWAY_BREAK_GLASS_ALLOWED_MODES`)
- **Static token hardening** (lockout window + rotation recommended signal)
- **Approval/review governance modes** (`BREAK_GLASS_APPROVAL_MODE`)
- **Post-use review event trail** for emergency sessions
- **Active token revocation + denylist foundation** for break-glass JWTs
- **Staging revocation drill + evidence gate** (Faz 123): `docs/break-glass-revocation-staging-drill.md`
- **Session limit** guardrail (in-memory per instance; max active sessions default 1)

## Credential strategy (Faz 90)

- A single pre-provisioned **static emergency token**, stored offline by security operations.
- identity-service stores only **`BREAK_GLASS_TOKEN_HASH`** (recommended `sha256:<hex>`).
- Operators submit the token only to `POST /auth/break-glass/login`; the raw token is never stored or emitted.

## Endpoint

`POST /auth/break-glass/login`

Request:

```json
{ "token": "<secret>", "reason": "Recover admin access after RBAC override misconfiguration..." }
```

Response:

```json
{ "accessToken": "<jwt>", "tokenType": "Bearer", "expiresIn": 900, "breakGlass": true }
```

Notes:
- Access token includes `break_glass=true` and `token_type=break_glass_admin`.
- It carries `platform_roles=["PLATFORM_ADMIN"]` and `platform_permissions=allPermissions()` but **api-gateway** can still block writes by policy.

## Gateway enforcement

- Break-glass tokens are rejected unless `GATEWAY_BREAK_GLASS_ADMIN_ALLOWED=true`.
- For break-glass sessions, non-GET admin requests are blocked unless `BREAK_GLASS_ALLOW_ADMIN_WRITE=true`.

## Defaults / production stance

- All break-glass flags remain **off** in production until a dedicated security review.
- Any use should trigger immediate rotation and post-incident review (see runbook).

## Phase 91 foundations

- WebAuthn challenge/verify foundation:
  - `POST /auth/break-glass/webauthn/challenge`
  - `POST /auth/break-glass/webauthn/verify`
- Offline signed assertion foundation:
  - `POST /auth/break-glass/offline-signed/login`
- Replay guard for offline assertions via `jti`.
- Session claims now include `break_glass_mode`.

See:
- `docs/break-glass-webauthn.md`
- `docs/break-glass-offline-signed-token.md`

## Future hardening (post Faz 91)

## Phase 92 governance foundation

- Emergency access events are persisted for review workflow.
- Post-use review mode (`post_use_review`) creates `PENDING_REVIEW` events.
- Review API and UI are feature-gated and disabled by default.
- Required-before-issue mode is a foundation path and not a production default.

See:
- `docs/break-glass-approval-workflow.md`
- `docs/break-glass-post-use-review.md`
- `docs/break-glass-token-revocation.md`
- `docs/break-glass-denylist.md`
- `docs/break-glass-token-rotation.md` (Faz 94 rotation governance)
- `docs/break-glass-token-rotation-runbook.md` (Faz 94 operator flow)

- WebAuthn-based emergency account flow
- Offline signed one-time JWT flow
- Approval workflow / 4-eyes emergency enablement
- Persistent session tracking (cluster-wide)

