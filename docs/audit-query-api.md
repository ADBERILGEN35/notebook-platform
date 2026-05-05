# Audit Query API

Faz 23 exposes service-local audit event queries for internal/admin operations. There is no central
audit-service and no public user-facing route.

## Endpoints

Each service owns its own audit table and endpoint:

- identity-service: `GET /internal/audit-events`
- workspace-service: `GET /internal/audit-events`
- content-service: `GET /internal/audit-events`

These endpoints must not be routed through the public gateway.

## Authorization

Audit query requires service JWT in:

```http
X-Service-Authorization: Bearer <service-jwt>
```

Required scope:

- `internal:audit:read`

Normal user access tokens are not accepted because there is no platform-admin user role or admin UI
yet. Static token fallback is intentionally not part of the production audit query model.

## Filters

Query parameters:

| Parameter | Type | Notes |
|---|---|---|
| `eventType` | string | exact match |
| `actorUserId` | UUID | exact match |
| `workspaceId` | UUID | exact match; identity events usually have null workspace |
| `aggregateType` | string | exact match |
| `aggregateId` | UUID | exact match |
| `requestId` | string | exact match |
| `createdFrom` | ISO timestamp | inclusive |
| `createdTo` | ISO timestamp | inclusive |
| `page` | integer | default `0` |
| `size` | integer | default `50`, max `200` |
| `sort` | string | default `createdAt,desc` |

Allowed sort fields:

- `createdAt`
- `eventType`
- `aggregateType`

Rules:

- `createdFrom <= createdTo`
- maximum time range is 90 days
- invalid UUID/timestamp/sort/page size returns `400`

## Response

The response is a page wrapper. workspace-service and content-service use the existing
`PageResponse<T>` shape; identity-service returns the same fields for audit:

```json
{
  "items": [
    {
      "id": "11111111-1111-1111-1111-111111111111",
      "eventType": "REFRESH_TOKEN_REVOKED",
      "actorUserId": "22222222-2222-2222-2222-222222222222",
      "workspaceId": null,
      "aggregateType": "REFRESH_TOKEN",
      "aggregateId": "33333333-3333-3333-3333-333333333333",
      "requestId": "request-id",
      "ipAddress": "203.0.113.10",
      "userAgent": "curl/8",
      "metadata": {"reason": "USER_LOGOUT"},
      "createdAt": "2026-05-05T10:00:00Z"
    }
  ],
  "page": 0,
  "size": 50,
  "totalElements": 1,
  "totalPages": 1,
  "hasNext": false,
  "hasPrevious": false
}
```

## Security Notes

- Metadata is sanitized again before response serialization.
- Keys containing password/token/secret/key/private/authorization/cookie are masked.
- IP address and user agent are returned for incident triage in this MVP. Privacy-sensitive
  deployments can add masking/anonymization later.
- Metadata JSONB contains-search is not implemented in this phase.

## Example

```bash
curl -sS 'http://localhost:8082/internal/audit-events?eventType=WORKSPACE_CREATED&size=25' \
  -H "X-Service-Authorization: Bearer $SERVICE_JWT"
```
