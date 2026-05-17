# Break-glass Token Revocation (Phase 93)

Phase 93 adds active break-glass access token revocation.

## Overview

- Break-glass JWT now carries `jti`, `break_glass_session_id`, and `break_glass_event_id`.
- Revocation stores token `jti` in denylist.
- Gateway checks denylist before allowing break-glass requests.

## APIs

- Internal identity:
  - `GET /internal/admin/break-glass/sessions`
  - `POST /internal/admin/break-glass/sessions/{ref}/revoke`
  - `POST /internal/admin/break-glass/sessions/revoke-all-active`
  - `POST /internal/admin/break-glass/events/{id}/revoke-token` (legacy by event id)
  - `GET /internal/break-glass/tokens/{jti}/revoked`
- Gateway admin:
  - `GET /admin/security/break-glass/sessions`
  - `POST /admin/security/break-glass/sessions/{ref}/revoke`
  - `POST /admin/security/break-glass/sessions/revoke-all-active`
  - `POST /admin/break-glass/events/{id}/revoke-token` (legacy)

## Security

- No token raw values are returned in UI.
- Revocation requires review-grade permission + MFA at gateway.
- Fail-closed mode is supported for denylist lookup failures.

See also: `docs/break-glass-token-rotation.md` for the Faz 94 rotation
governance flow that runs after the token is used.
