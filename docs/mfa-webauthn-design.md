# MFA / WebAuthn Design (Faz 51)

## Scope Decision

Faz 51 upgrades the foundation to working MFA step-up:

- login can return `mfaRequired=true` with `mfaSessionId`
- WebAuthn registration/authentication options + verify endpoints are active
- recovery code generate/verify is active (hash-only storage)
- credential list/rename/remove management is active

Library choice:

- `com.yubico:webauthn-server-core` is integrated as the baseline WebAuthn server library for
  standards-aligned request/response modeling and future verification hardening.

## Data Model

- `user_webauthn_credentials`
- `user_mfa_settings`
- `user_mfa_recovery_codes` (hash only; no plaintext storage)

## Challenge Strategy

Redis challenge store is active:

- `webauthn:registration:{userId}`
- `webauthn:authentication:{sessionId}`
- TTL: `MFA_CHALLENGE_TTL_SECONDS` (default 300)

- `mfa:session:{mfaSessionId}`
- `webauthn:registration:{userId}`
- `webauthn:authentication:{mfaSessionId}`
- single-use verification deletes challenge/session entries.

## API Contracts

- `GET /auth/mfa/settings`
- `POST /auth/mfa/webauthn/registration/options`
- `POST /auth/mfa/webauthn/registration/verify`
- `POST /auth/mfa/webauthn/authentication/options`
- `POST /auth/mfa/webauthn/authentication/verify`
- `POST /auth/mfa/recovery-codes/generate`
- `POST /auth/mfa/recovery-codes/verify`
- `GET/PATCH/DELETE /auth/mfa/webauthn/credentials/{credentialId}`

When disabled, endpoints return `MFA_NOT_ENABLED` (501). WebAuthn origin is validated with
`MFA_WEBAUTHN_ALLOWED_ORIGINS`.

## Security Notes

- Passkeys reduce phishing risk compared to password-only auth.
- Recovery codes are high-risk secrets; only hashed values are persisted.
- Admin MFA enforcement is designed as future configurable policy (`MFA_REQUIRED_FOR_PLATFORM_ADMIN`).

## SSO interaction (Faz 60)

- OIDC login can be configured to trust IdP MFA claims via `SSO_TRUST_IDP_MFA`.
- Optional constraints:
  - `SSO_REQUIRED_ACR`
  - `SSO_REQUIRED_AMR`
- Default posture keeps trust disabled and requires platform MFA policy where enforced.
