# Break-glass static-token rotation governance (Faz 94)

The break-glass static-token is provisioned out-of-band (External Secret / GitOps).
Faz 94 adds a tracked, audited governance flow around the *rotation* of that token
so a security team can prove that:

1. A rotation was triggered by a real use of the emergency token.
2. A human acknowledged the rotation request.
3. The configured hash on the identity-service was actually replaced (verified).
4. The change request is closed only after verification.

The platform never generates, accepts, displays, or transmits a raw token or raw
hash. Only short fingerprints (configurable prefix of `SHA-256(configured_hash)`)
are stored and displayed, so a reviewer can distinguish "before" vs "after"
without seeing the secret.

## State machine

`break_glass_token_rotation_events.status`:

```
REQUIRED ──acknowledge──> ACKNOWLEDGED ──verify──> VERIFIED ──close──> CLOSED
        \─verify (skip ack)─┘
```

- **REQUIRED** — created automatically the first time the configured static token is
  used (when `BREAK_GLASS_STATIC_TOKEN_ROTATION_TRACKING_ENABLED=true`). Duplicate
  REQUIRED events are suppressed per `oldTokenHashFingerprint`.
- **ACKNOWLEDGED** — a security admin has started the rotation procedure
  (External Secret update, runbook, etc.).
- **VERIFIED** — identity-service has recomputed the fingerprint of the currently
  configured `BREAK_GLASS_TOKEN_HASH`; it differs from `oldTokenHashFingerprint`,
  so the rotation must have happened.
- **CLOSED** — incident review complete. Only `VERIFIED` → `CLOSED` is allowed.

## Endpoints

Gateway (`api-gateway`):
- `GET    /admin/break-glass/rotation-events?status=&page=&size=`
- `GET    /admin/break-glass/rotation-events/{id}`
- `POST   /admin/break-glass/rotation-events/{id}/acknowledge`
- `POST   /admin/break-glass/rotation-events/{id}/verify`
- `POST   /admin/break-glass/rotation-events/{id}/close`

Identity-service internal:
- `/internal/admin/break-glass/rotation-events/...` mirrors the gateway paths.

## Permissions / MFA

Gateway authorizer (`AdminAuthorizationService`):
- `admin:break-glass:rotation:read` — required for GETs.
- `admin:break-glass:rotation:manage` — required for acknowledge / verify / close.
- Manage endpoints additionally require admin-write MFA when MFA mode is not `off`.

The internal API verifies a signed service JWT with one of:
- `internal:admin:break-glass:rotation:read`
- `internal:admin:break-glass:rotation:manage`

## Verification logic

The identity-service computes `fp:sha256(props.tokenHash)[0..N]` where `N` is
`BREAK_GLASS_TOKEN_HASH_FINGERPRINT_LENGTH` (clamped to 8..32). Verification
compares against the original `oldTokenHashFingerprint`:

- Different → status moves to `VERIFIED` and the new fingerprint is persisted.
- Same → `BREAK_GLASS_ROTATION_NOT_CHANGED` (HTTP 409). The runbook step that
  updates the External Secret has not been applied (or the new pods have not
  picked up the new value yet).

There is no endpoint that accepts a token value; the verify step only inspects
locally configured material.

## Audit events

- `BREAK_GLASS_TOKEN_ROTATION_REQUIRED`
- `BREAK_GLASS_TOKEN_ROTATION_DUPLICATE_SUPPRESSED`
- `BREAK_GLASS_TOKEN_ROTATION_LIMIT_REACHED`
- `BREAK_GLASS_TOKEN_ROTATION_ACKNOWLEDGED`
- `BREAK_GLASS_TOKEN_ROTATION_VERIFIED`
- `BREAK_GLASS_TOKEN_ROTATION_VERIFY_FAILED`
- `BREAK_GLASS_TOKEN_ROTATION_CLOSED`
- `BREAK_GLASS_TOKEN_ROTATION_VIEWED`

All entries carry `rotationEventId`, `oldFingerprint`, optional `newFingerprint`,
and `reasonPresent`. No actor email, raw hash, or token text is included.

## Status integration

`GET /auth/break-glass/status` and `GET /admin/enterprise/status` now expose:

```json
{
  "rotationTrackingEnabled": true,
  "rotationRequired": true,
  "openRotationEvents": 1,
  "oldestRotationRequiredAt": "...",
  "lastRotationVerifiedAt": "..."
}
```

## Configuration (identity-service)

| Env var | Default | Notes |
| --- | --- | --- |
| `BREAK_GLASS_STATIC_TOKEN_ROTATION_TRACKING_ENABLED` | `false` | Master switch. |
| `BREAK_GLASS_ROTATION_API_ENABLED` | `false` | Required for `acknowledge`/`verify`/`close`. |
| `BREAK_GLASS_TOKEN_HASH_FINGERPRINT_LENGTH` | `12` | Clamped to 8..32. |
| `BREAK_GLASS_ROTATION_MAX_OPEN_EVENTS` | `5` | Hard cap on REQUIRED+ACKNOWLEDGED. |

Helm values mirror these (`config.breakGlassRotationTrackingEnabled`,
`config.breakGlassRotationApiEnabled`,
`config.breakGlassTokenHashFingerprintLength`,
`config.breakGlassRotationMaxOpenEvents`).

## Frontend

- Feature flag: `FRONTEND_BREAK_GLASS_ROTATION_UI_ENABLED=false` (default).
- Page: `/app/admin/security/break-glass/rotation`.
- Shows a rotation summary (required / open / oldest), an events table with
  short fingerprints, and an action panel for acknowledge / verify / close.
- The page never shows a raw token or hash, and the helper text states this
  explicitly.

## What this faz does NOT do

- It does not generate a new token, send one, store one in plaintext, or call
  an External Secret provider on the user's behalf.
- It does not unblock destructive admin writes from a break-glass session.
- It does not assign a permanent admin role to anyone.
- It does not implement multi-approver rotation, IdP-driven rotation, or
  provider-secret automation. Those remain future work.
