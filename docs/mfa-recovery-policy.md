# MFA Recovery Policy

## Recovery Code Lifecycle

- Recovery codes are generated as plaintext once per request.
- Only hashed values are stored in the database.
- Each code is one-time use (`used_at` is set on successful verification).
- Regeneration revokes old unused codes (`revoked_at`) and creates 10 new codes.

## API Contract

`GET /auth/mfa/settings` includes:

- `webauthnEnabled`
- `backupCodesEnabled`
- `recoveryCodesRemaining`
- `activeCredentialCount`
- `mfaRequired`

`POST /auth/mfa/recovery-codes/generate` accepts:

- `acknowledgeReplace` (required when active codes exist)

If regeneration is attempted without acknowledgement while active codes exist:

- `400 MFA_RECOVERY_REGEN_ACK_REQUIRED`

## UX Policy

Settings/Security page:

- Shows recovery code remaining count.
- Distinguishes generate vs regenerate action.
- Shows codes once and requires explicit user acknowledgement:
  `I have saved these recovery codes.`

## Operational Notes

- Recovery codes are a fallback for passkey loss.
- They are not a primary MFA method and should be rotated after use.
- Support runbook should prioritize passkey re-enrollment after recovery code login.

## Identity audit events (Faz 52 cleanup)

Identity audit stream records MFA recovery lifecycle events without sensitive payloads:

- `MFA_RECOVERY_CODES_GENERATED`
- `MFA_RECOVERY_CODES_REGENERATED`
- `MFA_RECOVERY_CODE_USED`
- `MFA_CREDENTIAL_REVOKED`

