# SCIM Bulk Operations (Faz 76)

## Endpoint

- `POST /scim/v2/Bulk` on **identity-service** (also reachable via api-gateway `Path=/scim/v2/**`).
- Requires the same SCIM bearer token as other SCIM routes.
- Feature flag: `SCIM_BULK_ENABLED` (default **false**). When disabled: `501` with detail `SCIM_BULK_DISABLED`.
- **Faz 77:** platform admins can open an audited **change request** for desired `SCIM_BULK_ENABLED` state (`docs/enterprise-admin-write-operations.md`); no automatic config apply.

## Request (MVP)

Schema: `urn:ietf:params:scim:api:messages:2.0:BulkRequest`.

- `failOnErrors`: optional; processing stops when the error count reaches this threshold. Clamped to at most `SCIM_BULK_FAIL_ON_ERRORS_MAX` (default `10`). Values `<= 0` are treated as `1`.
- `Operations`: array of `{ method, path, bulkId?, data? }`.
- Maximum operations per request: `SCIM_BULK_MAX_OPERATIONS` (default `100`). Exceeded requests return `400` with `SCIM_BULK_TOO_MANY_OPERATIONS`.

## Supported operations

| Method | Path pattern | Body |
|--------|----------------|------|
| POST | `/Users` | SCIM User |
| PUT | `/Users/{uuid}` | SCIM User |
| PATCH | `/Users/{uuid}` | SCIM PatchOp |
| DELETE | `/Users/{uuid}` | — |
| POST | `/Groups` | SCIM Group |
| PUT | `/Groups/{uuid}` | SCIM Group |
| PATCH | `/Groups/{uuid}` | SCIM PatchOp |
| DELETE | `/Groups/{uuid}` | — |

Paths may omit leading `/`; query strings are stripped.

## Atomicity

- **Non-transactional MVP**: each operation runs in its own service transaction. Earlier successes are **not** rolled back when a later operation fails or when `failOnErrors` stops the batch.
- Document this behavior to IdP integrators; do not assume all-or-nothing bulk.

## bulkId references

- After a successful `POST /Users` or `POST /Groups`, the service records `bulkId -> created resource id` for the same bulk request only.
- Group or user membership `value` may use `bulkId:<id>` to reference a resource created earlier in the same bulk payload.
- Unresolved `bulkId` references return `400` with `SCIM_INVALID_GROUP_MEMBER` / unresolved bulkId messaging.

## Security and logging

- Do not log raw bulk bodies or bearer tokens.
- Audit events: `SCIM_BULK_REQUEST_RECEIVED`, `SCIM_BULK_OPERATION_FAILED`, `SCIM_BULK_COMPLETED` with counts only (`operationCount`, `successCount`, `errorCount`, `failOnErrors`), never raw payload.

## Rate limiting

- Gateway SCIM rate limits apply to `/scim/v2/**` including `/Bulk`. Tune Redis bucket settings if bulk traffic is enabled (`SCIM_RATE_LIMIT_*` in Helm/config).

## Rollout

- Keep `SCIM_BULK_ENABLED=false` in production until an IdP validates behavior in dev/staging.
- Faz 97 adds provider capability flag `SCIM_PROVIDER_SUPPORTS_BULK`; this is diagnostics metadata only and does not change `/Bulk` behavior or enable production bulk rollout.
