# Break-glass Offline Signed Assertion Foundation (Phase 91)

This document describes the Phase 91 offline signed assertion foundation for break-glass login.

## Goal

Reduce static-token exposure risk by allowing short-lived offline assertions signed outside the platform.

## Config

- `BREAK_GLASS_OFFLINE_SIGNED_ENABLED=false`
- `BREAK_GLASS_OFFLINE_PUBLIC_KEY_PATH=`
- `BREAK_GLASS_OFFLINE_ALLOWED_ISSUER=notebook-break-glass-offline`
- `BREAK_GLASS_OFFLINE_REQUIRED_AUDIENCE=identity-service`
- `BREAK_GLASS_OFFLINE_MAX_ASSERTION_TTL_SECONDS=300`

## Endpoint

- `POST /auth/break-glass/offline-signed/login`

Request includes:

- `assertion` (JWT-like offline assertion)
- `reason` (mandatory emergency reason)

## Validation guards

- Issuer and audience checks are mandatory.
- Assertion purpose must be `break_glass_admin`.
- Expiry and max TTL checks are enforced.
- Replay guard uses `jti`; repeated `jti` is rejected.
- Session token is short-lived and no refresh token is issued.

## Security constraints

- Private key is never stored in frontend and is never returned by APIs.
- Raw assertion is not exposed in status responses.
- Gateway still applies allowed-modes checks and write blocking defaults.
- Phase 92 governance can require post-use review tracking for issued sessions.
