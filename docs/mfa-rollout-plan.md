# MFA Rollout Plan (Faz 50 Foundation)

## Phase 1: Foundation (current)

- Ship schema + API contracts + feature flags disabled by default
- Expose read-only security UI state in frontend settings
- Validate environment config and operational observability

## Phase 2: WebAuthn Core

- Integrate standards-compliant verification library
- Implement registration/assertion challenge lifecycle via Redis
- Add audit events for enrollment/removal/recovery usage

## Phase 3: Login Step-up

- Add `mfaRequired` login state and short-lived MFA continuation token
- Complete verification endpoints and cookie issuance only after MFA success
- Extend auth claims (`amr`, `mfa_verified`, `mfa_verified_at`)

## Phase 4: Enforcement

- Optional admin-first enforcement (`MFA_REQUIRED_FOR_PLATFORM_ADMIN=true`)
- Progressive rollout by environment/allowlist and user cohort
- Recovery and support runbooks for device loss scenarios
