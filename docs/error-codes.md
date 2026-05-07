# Error Codes

| errorCode | HTTP status | service | meaning | example scenario |
|---|---:|---|---|---|
| VALIDATION_ERROR | 400 | all | Request validation failed | Missing required field |
| INVALID_PAGE_REQUEST | 400 | workspace/content | Page index invalid | `page=-1` |
| INVALID_PAGE_SIZE | 400 | workspace/content | Page size outside allowed range | `size=0` or `size=101` |
| INVALID_SORT_FIELD | 400 | workspace/content | Sort field is not allow-listed | `sort=passwordHash,asc` |
| INVALID_SORT_DIRECTION | 400 | workspace/content | Sort direction is not `asc` or `desc` | `sort=createdAt,sideways` |
| INVALID_AUDIT_FILTER | 400 | identity/workspace/content | Audit query filter cannot be parsed or is invalid | bad UUID/timestamp/page |
| INVALID_AUDIT_TIME_RANGE | 400 | identity/workspace/content | `createdFrom` is after `createdTo` | inverted audit time range |
| AUDIT_QUERY_RANGE_TOO_LARGE | 400 | identity/workspace/content | Audit query time range exceeds 90 days | `createdFrom` one year before `createdTo` |
| AUDIT_ACCESS_DENIED | 403 | identity/workspace/content | Service JWT lacks audit scope | missing `internal:audit:read` |
| INTERNAL_SERVER_ERROR | 500 | identity-service | Unexpected identity error | Unhandled runtime error |
| INVALID_CREDENTIALS | 401 | identity-service | Login credentials invalid | Wrong email/password |
| USER_DISABLED | 403 | identity-service | User cannot authenticate | Disabled/deleted user |
| EMAIL_ALREADY_EXISTS | 409 | identity-service | Email is already registered | Duplicate signup |
| INVALID_REFRESH_TOKEN | 401 | identity-service | Refresh token invalid | Reuse/revoked token |
| ACCESS_TOKEN_REQUIRED | 401 | identity-service | Access token missing or invalid for protected auth endpoint | Call `/auth/revoke-all` without bearer token |
| INVALID_TOKEN_TYPE | 401 | identity-service/api-gateway | Token is not valid for the endpoint | Refresh token sent to revoke-all |
| REFRESH_TOKEN_USER_MISMATCH | 403 | identity-service | Refresh token belongs to another user | Logout with another user's refresh token |
| MISSING_ACCESS_TOKEN | 401 | api-gateway | Bearer token missing | Protected request without token |
| INVALID_ACCESS_TOKEN | 401 | api-gateway | Access token invalid | Bad signature/malformed token |
| EXPIRED_ACCESS_TOKEN | 401 | api-gateway | Access token expired | Expired JWT |
| INVALID_WORKSPACE_ID | 400 | api-gateway | Workspace header is not UUID | Bad `X-Workspace-Id` |
| RATE_LIMIT_EXCEEDED | 429 | api-gateway | Request bucket exhausted | Too many auth requests |
| ROUTE_UNAVAILABLE | 503 | api-gateway | Downstream route unavailable | Service connection refused |
| MISSING_USER_CONTEXT | 401 | workspace/content | User header missing | Direct call without `X-User-Id` |
| INTERNAL_AUTH_REQUIRED | 401 | workspace/content/identity internal APIs | Internal auth header missing | internal API call without service JWT |
| INVALID_INTERNAL_TOKEN | 401 | workspace-service | Static internal token invalid | Wrong `X-Internal-Token` |
| INVALID_SERVICE_JWT | 401 | internal APIs | Service JWT malformed, wrong kid/type or bad signature | Bad `X-Service-Authorization` |
| EXPIRED_SERVICE_JWT | 401 | internal APIs | Service JWT expired | Expired internal JWT |
| INVALID_SERVICE_AUDIENCE | 401 | internal APIs | Service JWT audience mismatch | `aud` is not target service |
| INVALID_SERVICE_ISSUER | 401 | internal APIs | Service JWT issuer mismatch | untrusted `iss` |
| INSUFFICIENT_SERVICE_SCOPE | 403 | internal APIs | Service JWT lacks endpoint scope | tag scope used for permission endpoint |
| WORKSPACE_NOT_FOUND | 404 | workspace-service | Workspace not found | Unknown workspace id |
| WORKSPACE_ACCESS_DENIED | 403 | workspace-service | Workspace permission denied | MEMBER updates owner role |
| LAST_OWNER_CANNOT_BE_REMOVED | 409 | workspace-service | Owner safety violation | Remove final OWNER |
| LAST_OWNER_CANNOT_BE_CHANGED | 409 | workspace-service | Owner safety violation | Downgrade final OWNER |
| DUPLICATE_WORKSPACE_SLUG | 409 | workspace-service | Slug already exists | Manual duplicate slug |
| PERSONAL_WORKSPACE_ALREADY_EXISTS | 409 | workspace-service | User already has personal workspace | Second personal workspace |
| DUPLICATE_TAG | 409 | workspace-service | Tag duplicate | Same name/scope in workspace |
| NOTEBOOK_NOT_FOUND | 404 | workspace-service | Notebook not found | Internal permission lookup for unknown notebook |
| INVALID_INVITATION_TOKEN | 400 | workspace-service | Invite token invalid | Unknown token |
| NOTIFICATION_SERVICE_UNAVAILABLE | 503 | workspace-service | Invitation email enqueue failed | notification-service down |
| NOTIFICATION_RECIPIENT_SUPPRESSED | 409 | workspace-service | Invitation email recipient is suppressed | bounced/complained address |
| INVITATION_EXPIRED | 400 | workspace-service | Invite expired | `expiresAt` is past |
| INVITATION_REVOKED | 400 | workspace-service | Invite revoked | `revokedAt` set |
| INVITATION_ALREADY_ACCEPTED | 400 | workspace-service | Invite already used | `acceptedAt` set |
| NOTE_NOT_FOUND | 404 | content-service | Note not found | Unknown note id |
| NOTE_VERSION_NOT_FOUND | 404 | content-service | Note version not found | Unknown version number |
| NOTEBOOK_NOT_FOUND | 404 | content-service | Workspace notebook contract says notebook is missing | Create note under unknown notebook |
| COMMENT_NOT_FOUND | 404 | content-service | Comment not found | Deleted/unknown comment |
| NOTEBOOK_ACCESS_DENIED | 403 | content-service | Notebook permission denied | VIEWER tries comment |
| COMMENT_ACCESS_DENIED | 403 | content-service | Comment permission denied | User edits another comment |
| INVALID_BLOCK_CONTENT | 400 | content-service | Block JSON invalid | Unknown block type |
| INVALID_NOTE_LINK | 400 | content-service | Note link invalid | Target note missing |
| INVALID_WORKSPACE_CONTEXT | 400 | content-service | Workspace header conflict | `X-Workspace-Id` mismatch |
| DUPLICATE_NOTE_TAG | 409 | content-service | Tag already attached | Reattach same tag |
| TAG_NOT_FOUND | 404 | content-service | Workspace tag missing | Attach unknown tag |
| WORKSPACE_SERVICE_UNAVAILABLE | 503 | content-service | Permission dependency failed | Workspace permission client down |
| NOTIFICATION_ACCESS_DENIED | 401/403 | notification-service | Missing/insufficient service JWT | normal user token or wrong scope |
| INVALID_NOTIFICATION_REQUEST | 400 | notification-service | Notification request invalid | missing template variable |
| EMAIL_TEMPLATE_NOT_FOUND | 400 | notification-service | Template key/resource missing | unknown template |
| EMAIL_PROVIDER_UNAVAILABLE | 503 | notification-service | Email provider cannot accept message | SMTP outage |
| EMAIL_SEND_FAILED | 500 | notification-service | Email send failed unexpectedly | provider runtime error |
| DUPLICATE_NOTIFICATION | 409 | notification-service | Duplicate idempotency key | future strict duplicate mode |
| INVALID_EMAIL_PROVIDER_CONFIG | 500 | notification-service | Email provider config invalid | unsupported provider |
| EMAIL_WEBHOOK_DISABLED | 403 | notification-service | Provider webhook endpoint is disabled | webhook called before enablement |
| INVALID_EMAIL_WEBHOOK_SIGNATURE | 401 | notification-service | Webhook signature invalid | bad HMAC |
| EMAIL_WEBHOOK_REPLAY_REJECTED | 401 | notification-service | Webhook timestamp missing or outside tolerance | stale provider event |
| EMAIL_WEBHOOK_EVENT_INVALID | 400 | notification-service | Webhook payload cannot be parsed | invalid provider JSON |
| EMAIL_PROVIDER_EVENT_DUPLICATE | 200 | notification-service | Duplicate provider event ignored idempotently | repeated webhook |
| EMAIL_RECIPIENT_SUPPRESSED | 409 | notification-service | Recipient is on active suppression list | bounced recipient |
| EMAIL_SUPPRESSION_ACCESS_DENIED | 403 | notification-service | Suppression ops scope is missing | wrong service JWT scope |
| EMAIL_SUPPRESSION_ALREADY_EXISTS | 409 | notification-service | Active suppression already exists | duplicate manual suppression |
| EMAIL_SUPPRESSION_NOT_FOUND | 404 | notification-service | Suppression record was not found | release unknown id |
| INVALID_EMAIL_SUPPRESSION_REQUEST | 400 | notification-service | Suppression request is invalid | missing email/reason |
| EMAIL_PROVIDER_READINESS_FAILED | 1 | scripts | Email provider readiness check failed | missing DNS/provider config |
| EMAIL_SUPPRESSION_NOT_FOUND | 404 | notification-service | Suppression record not found | future operator API |
| SEARCH_ACCESS_DENIED | 401/403 | search-service | Missing/insufficient search auth | missing service JWT |
| INVALID_SEARCH_QUERY | 400 | search-service | Search query is missing or invalid | blank q |
| SEARCH_QUERY_TOO_SHORT | 400 | search-service | Search query shorter than configured minimum | q=a |
| SEARCH_QUERY_TOO_LONG | 400 | search-service | Search query exceeds configured maximum | q > 120 chars |
| SEARCH_INDEX_REQUEST_INVALID | 400 | search-service | Search indexing request invalid | missing noteId |
| SEARCH_SERVICE_UNAVAILABLE | 503 | content/search | Search dependency unavailable | indexing API down |
| SEARCH_PROVIDER_UNAVAILABLE | 503 | search-service | Selected search provider unavailable | OpenSearch down |
| OPENSEARCH_UNAVAILABLE | 503 | search-service | OpenSearch request failed | non-2xx or connection failure |
| SEARCH_PROVIDER_MISCONFIGURED | 400/503 | search-service | Provider config invalid or incomplete | missing OPENSEARCH_URL |
| SEARCH_PROVIDER_TIMEOUT | 503 | search-service | Provider request timed out | OpenSearch timeout |
| WORKSPACE_PERMISSION_UNAVAILABLE | 503 | search-service | Workspace permission filtering failed | workspace-service down |
| INVALID_WORKSPACE_CONTEXT | 400 | search-service | Header workspace and query workspace conflict | mismatched X-Workspace-Id |
| SEARCH_OUTBOX_EVENT_NOT_FOUND | 404 | content-service | Search outbox event missing | unknown event id |
| SEARCH_OUTBOX_REPROCESS_FAILED | 500 | content-service | Failed to requeue search outbox events | repository failure |
| SEARCH_OUTBOX_ACCESS_DENIED | 403 | content-service | Search outbox service JWT lacks scope | wrong ops scope |
| INVALID_SEARCH_OUTBOX_REQUEST | 400 | content-service | Invalid search outbox ops request | invalid limit |
| SEARCH_INDEX_SOURCE_ACCESS_DENIED | 403 | content-service | Search index source service JWT lacks scope | wrong source scope |
| INVALID_SEARCH_INDEX_SOURCE_REQUEST | 400 | content-service | Invalid search index source request | invalid cursor or size |
| REINDEX_ACCESS_DENIED | 401/403 | search-service | Reindex service JWT missing or lacks scope | missing manage scope |
| REINDEX_JOB_ALREADY_RUNNING | 409 | search-service | A reindex job is already pending/running | second job create |
| REINDEX_JOB_NOT_FOUND | 404 | search-service | Reindex job not found | unknown job id |
| INVALID_REINDEX_REQUEST | 400 | search-service | Invalid mode/scope combination | NOTEBOOK without notebookId |
| INVALID_CLEANUP_MODE | 400 | search-service | Invalid cleanup/dry-run combination | dryRunCleanup without cleanupOrphans |
| PREVIEW_NOT_READY | 409 | search-service | Orphan preview unavailable until successful scan completion | preview running job |
| ORPHAN_PREVIEW_ACCESS_DENIED | 401/403 | search-service | Orphan preview service JWT missing or lacks scope | missing manage scope |
| REINDEX_SOURCE_UNAVAILABLE | 503 | search-service | Content source API unavailable | content-service down |
| REINDEX_JOB_CANCELLED | 409 | search-service | Reindex job was cancelled | cancelled while polling |
| REINDEX_JOB_FAILED | 500 | search-service | Reindex job failed | max failures exceeded |

All error responses use:

```json
{
  "timestamp": "2026-04-29T12:00:00Z",
  "status": 400,
  "errorCode": "VALIDATION_ERROR",
  "message": "name: must not be blank",
  "path": "/workspaces",
  "requestId": "11111111-1111-1111-1111-111111111111",
  "fieldErrors": [{"field":"name","message":"must not be blank"}]
}
```
