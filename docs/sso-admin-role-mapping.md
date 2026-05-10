# SSO Admin Role Mapping (Faz 60)

## Goal

Keep existing allowlist model while enabling IdP-group based admin identity through identity-service.

## Mapping Flow

1. OIDC groups are read from provider claim (default `groups`).
2. If group intersects with `SSO_ADMIN_GROUPS`, identity-service adds:
   - `platform_roles: ["PLATFORM_ADMIN"]`
   - `sso_groups: [...]`
   - `auth_provider: "<registrationId>"`
3. api-gateway admin authorization checks:
   - `platform_roles` (preferred)
   - fallback legacy `roles`
   - fallback allowlist (`GATEWAY_ADMIN_ALLOWED_USER_IDS` / `GATEWAY_ADMIN_ALLOWED_EMAILS`)

## MFA Interaction

- Gateway admin MFA enforcement is unchanged.
- With `SSO_TRUST_IDP_MFA=false` (default), SSO login does not automatically satisfy platform MFA.
- With trust enabled and required `acr`/`amr` matched, identity-service can mark `mfa_verified=true`.

## SCIM parity (Faz 76)

- `SCIM_ADMIN_GROUPS` uses the same case-insensitive displayName/externalId token idea as `SSO_ADMIN_GROUPS`, but resolves against **SCIM effective group membership** (direct + nested active groups).
- Prefer matching stable `externalId` values from the IdP where possible.

## Rollout

1. Keep SSO disabled by default.
2. Enable in staging with non-privileged test group.
3. Enable admin group mapping and verify admin endpoint behavior.
4. Keep allowlist as emergency fallback during transition.
