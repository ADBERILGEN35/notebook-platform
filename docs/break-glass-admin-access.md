# Break-glass admin access (Faz 90)

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

### Guardrails implemented in Faz 90
- **Disabled by default** (`BREAK_GLASS_ENABLED=false`, `GATEWAY_BREAK_GLASS_ADMIN_ALLOWED=false`)
- **Short-lived access token only** (no refresh token)
- **Reason required** (minimum 20 chars; server validated)
- **No secret exposure** (no token/hash returned in status, no raw secret in logs)
- **Gateway write blocked by default** (`BREAK_GLASS_ALLOW_ADMIN_WRITE=false`)
- **Audit events** for attempt/success/failure/session issued (identity-service)
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

## Future hardening (not in Faz 90)

- WebAuthn-based emergency account flow
- Offline signed one-time JWT flow
- Approval workflow / 4-eyes emergency enablement
- Persistent session tracking (cluster-wide)

