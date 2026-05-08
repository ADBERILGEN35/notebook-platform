# Enterprise SSO (Faz 60)

## Scope

Faz 60 adds OIDC-based Enterprise SSO foundation for identity-service and frontend login UX.

Included:

- Generic OIDC provider configuration
- `/auth/sso/providers`, `/auth/sso/{provider}/authorize`, `/auth/sso/{provider}/callback`
- Redis-backed SSO `state`/`nonce` (short-lived)
- External identity mapping table (`external_identities`)
- Allowed-domain and verified-email enforcement
- Optional trust model for IdP MFA claims

Not included:

- SAML
- SCIM provisioning
- Full account-linking UI
- Enterprise admin management console

## Core Config

- `SSO_ENABLED`
- `SSO_STATE_TTL_SECONDS`
- `SSO_PROVIDER_ISSUER_URI`
- `SSO_PROVIDER_CLIENT_ID`
- `SSO_PROVIDER_CLIENT_SECRET` (secret only)
- `SSO_ALLOWED_DOMAINS`
- `SSO_GROUPS_CLAIM`
- `SSO_ADMIN_GROUPS`
- `SSO_TRUST_IDP_MFA`
- `SSO_REQUIRED_ACR`
- `SSO_REQUIRED_AMR`

## Linking Policy (MVP)

- `email_verified` must be `true`.
- Domain must match `SSO_ALLOWED_DOMAINS` when configured.
- Existing external identity (`provider + subject`) is reused.
- Otherwise, service links by normalized email or creates a new local user.

## Security Notes

- OIDC discovery + id_token signature and issuer/audience checks are required.
- `state` and `nonce` are single-use with TTL.
- Raw OAuth/OIDC tokens are not persisted and not returned to frontend.
- Stored external claims are sanitized for minimum required identity context.

## Operational visibility (Faz 63)

Secret-safe SSO configuration flags surface in the Enterprise Admin Console (`docs/enterprise-admin-console.md`)
via gateway aggregated status (no client secrets or raw issuer URLs in this phase).
