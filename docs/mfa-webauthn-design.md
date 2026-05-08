# MFA / WebAuthn Design (Faz 50)

## Scope Decision

Faz 50 uses **design + storage + API skeleton + frontend foundation** (not full cryptographic verification).

Included:

- WebAuthn data model and migration in `identity-service`
- MFA feature flags and endpoint contracts
- `/auth/mfa/*` API skeleton returning controlled disabled behavior
- frontend settings/security MFA section and browser capability detection

Not included:

- Full WebAuthn attestation/assertion cryptographic verification
- login step-up enforcement with `mfaToken` cookie/token
- production recovery code UX and device management

## Data Model

- `user_webauthn_credentials`
- `user_mfa_settings`
- `user_mfa_recovery_codes` (hash only; no plaintext storage)

## Challenge Strategy

Redis challenge store is the planned target:

- `webauthn:registration:{userId}`
- `webauthn:authentication:{sessionId}`
- TTL: `MFA_CHALLENGE_TTL_SECONDS` (default 300)

Faz 50 keeps this as design contract, not final implementation.

## API Contracts

- `GET /auth/mfa/settings`
- `POST /auth/mfa/webauthn/registration/options`
- `POST /auth/mfa/webauthn/registration/verify`
- `POST /auth/mfa/webauthn/authentication/options`
- `POST /auth/mfa/webauthn/authentication/verify`
- `POST /auth/mfa/recovery-codes/generate`
- `POST /auth/mfa/recovery-codes/verify`
- `GET/PATCH/DELETE /auth/mfa/webauthn/credentials/{credentialId}`

When disabled, endpoints return `MFA_NOT_ENABLED` (501).

## Security Notes

- Passkeys reduce phishing risk compared to password-only auth.
- Recovery codes are high-risk secrets; only hashed values are persisted.
- Admin MFA enforcement is designed as future configurable policy (`MFA_REQUIRED_FOR_PLATFORM_ADMIN`).
