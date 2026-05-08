# Admin Audit Proxy (Faz 43)

Faz 43 connects the SPA Admin/Audit UI to real service audit data through `api-gateway` without
leaking service credentials to browsers.

## Why this proxy exists

- Service-local audit endpoints (`/internal/audit-events`) require service JWT scope
  `internal:audit:read`.
- Browser sessions (cookie/bearer user tokens) must not receive service JWTs.
- Gateway enforces platform-admin style access and signs service JWTs server-side per target service.

## Public Contract

`GET /admin/audit-events`

Required query:

- `source=identity|workspace|content`

Optional query:

- `eventType`, `actorUserId`, `workspaceId`, `aggregateType`, `aggregateId`, `requestId`,
  `createdFrom`, `createdTo`, `page`, `size`, `sort`.

Validation:

- UUID fields must be valid.
- ISO datetime fields must parse.
- `createdFrom <= createdTo`.
- sort allow-list: `createdAt`, `eventType`, `aggregateType` with `asc|desc`.

## Authorization Model (MVP)

Request must be authenticated (`401` otherwise), and pass one of:

- JWT claim `roles` contains `PLATFORM_ADMIN` (`ROLE_PLATFORM_ADMIN`, `ADMIN`, `ROLE_ADMIN` also accepted in MVP).
- user `sub` is in `gateway.admin.allowed-user-ids`.
- user email claim is in `gateway.admin.allowed-emails`.

Switches:

- `gateway.admin.enabled=true`
- `gateway.admin.audit.enabled=true`

If disabled, gateway returns `404 ADMIN_AUDIT_DISABLED`.

## Source Routing + Internal Auth

Gateway maps `source` to service:

- `identity` -> `${IDENTITY_SERVICE_URL}/internal/audit-events`
- `workspace` -> `${WORKSPACE_SERVICE_URL}/internal/audit-events`
- `content` -> `${CONTENT_SERVICE_URL}/internal/audit-events`

Gateway signs `X-Service-Authorization: Bearer <jwt>` with:

- `token_type=service`
- `scope=internal:audit:read`
- `aud=<target-service>`
- short TTL (default 60s)

## Error Mapping

- `403 ADMIN_ACCESS_DENIED` for authenticated non-admins.
- `400 INVALID_AUDIT_SOURCE` for bad source.
- `400 INVALID_AUDIT_FILTER` for bad filter/sort/date inputs.
- `503 AUDIT_SOURCE_UNAVAILABLE` when target service is down/unavailable.
- `502 AUDIT_PROXY_INTERNAL_AUTH_FAILED` when internal service returns 401/403.
- `503 AUDIT_PROXY_REQUEST_FAILED` for other proxy failures.

Internal response payloads and secrets are not forwarded.

## Rate Limiting

Gateway applies dedicated per-user (`sub`) rate limit on `/admin/audit-events`:

- `ADMIN_AUDIT_RATE_LIMIT_REPLENISH_RATE`
- `ADMIN_AUDIT_RATE_LIMIT_BURST_CAPACITY`
- `ADMIN_AUDIT_RATE_LIMIT_REQUESTED_TOKENS`

## Audit Access Logging

Gateway structured logs include:

- `adminUserId`, `adminEmail`, `source`, `filters`, `requestId`.

Until dedicated persisted audit-of-audit events are added, these logs must be shipped to centralized
log/SIEM pipelines.

## Faz 48 Export Extension

Gateway also exposes `GET /admin/audit-events/export` with admin authorization and server-side
service JWT fan-out.

- Formats: `csv`, `jsonl`
- Required range: `createdFrom`, `createdTo`
- Export limits: range days + max records + internal page size
- Dedicated export rate-limit bucket (`admin-audit-export`)

## Faz 52 Admin MFA enforcement

Admin audit endpoints also follow gateway MFA policy:

- `gateway.admin.require-mfa`
- `gateway.admin.mfa-mode`
- `gateway.admin.mfa-accepted-methods`

When enforcement conditions fail, gateway returns `403 ADMIN_MFA_REQUIRED`.

## Faz 53 Scheduled export foundation

Scheduled export uses the existing export endpoint from an ops-side script/CronJob.
No service JWT is exposed to frontend, and gateway does not host an internal scheduler.

## Rollout

1. Deploy gateway with `gateway.admin.audit.enabled=false`.
2. Staging: enable gateway admin audit + allowlist.
3. Frontend: `ADMIN_UI_ENABLED=true`, `AUDIT_API_MODE=real`.
4. Validate non-admin `403`, admin success, source-unavailable mapping.
5. Production: start allowlist, then migrate to strict `PLATFORM_ADMIN` claim model.
