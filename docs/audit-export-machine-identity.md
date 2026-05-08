# Audit Export Machine Identity (Faz 54)

## Why

Scheduled export should not depend on admin browser cookie/session flows.
Machine identity provides scoped, short-lived non-human auth for CronJob automation.

## JWT claim model

- `token_type=machine`
- `iss` allowlisted (example: `https://audit-exporter.internal`)
- `sub` service principal id (example: `audit-exporter`)
- `aud=api-gateway`
- `scope=admin:audit:export`
- `iat`, `exp`, `jti`
- optional `service_name`

## Gateway behavior

- Machine auth applies only to `GET /admin/audit-events/export`.
- `/admin/audit-events` query remains user-admin path only.
- User flow still requires admin authorization + MFA policy.
- Machine flow skips MFA because principal is non-human and scope-restricted.

## Config

- `GATEWAY_AUDIT_EXPORT_MACHINE_AUTH_ENABLED`
- `GATEWAY_AUDIT_EXPORT_MACHINE_AUTH_ALLOWED_ISSUERS`
- `GATEWAY_AUDIT_EXPORT_MACHINE_AUTH_AUDIENCE`
- `GATEWAY_AUDIT_EXPORT_MACHINE_AUTH_REQUIRED_SCOPE`
- `GATEWAY_AUDIT_EXPORT_MACHINE_AUTH_MAX_TTL_SECONDS`

## Errors

- `AUDIT_EXPORT_MACHINE_AUTH_DISABLED`
- `AUDIT_EXPORT_MACHINE_TOKEN_REQUIRED`
- `INVALID_AUDIT_EXPORT_MACHINE_TOKEN`
- `EXPIRED_AUDIT_EXPORT_MACHINE_TOKEN`
- `INVALID_AUDIT_EXPORT_MACHINE_ISSUER`
- `INVALID_AUDIT_EXPORT_MACHINE_AUDIENCE`
- `INSUFFICIENT_AUDIT_EXPORT_MACHINE_SCOPE`
- `AUDIT_EXPORT_MACHINE_TOKEN_TTL_TOO_LONG`

## Key handling

- Private key is mounted only into CronJob.
- Token value is never logged.
- Public verification material is gateway config/secret managed.
- Rotate keys with overlap window and staged rollout.

Machine identity is orthogonal to object storage credentials; S3 keys must remain separate secrets.
