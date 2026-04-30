# Error Codes

| errorCode | HTTP status | service | meaning | example scenario |
|---|---:|---|---|---|
| VALIDATION_ERROR | 400 | all | Request validation failed | Missing required field |
| INTERNAL_SERVER_ERROR | 500 | identity-service | Unexpected identity error | Unhandled runtime error |
| INVALID_CREDENTIALS | 401 | identity-service | Login credentials invalid | Wrong email/password |
| USER_DISABLED | 403 | identity-service | User cannot authenticate | Disabled/deleted user |
| EMAIL_ALREADY_EXISTS | 409 | identity-service | Email is already registered | Duplicate signup |
| INVALID_REFRESH_TOKEN | 401 | identity-service | Refresh token invalid | Reuse/revoked token |
| MISSING_ACCESS_TOKEN | 401 | api-gateway | Bearer token missing | Protected request without token |
| INVALID_ACCESS_TOKEN | 401 | api-gateway | Access token invalid | Bad signature/malformed token |
| EXPIRED_ACCESS_TOKEN | 401 | api-gateway | Access token expired | Expired JWT |
| INVALID_TOKEN_TYPE | 401 | api-gateway | Token is not access token | Refresh token sent to protected route |
| INVALID_WORKSPACE_ID | 400 | api-gateway | Workspace header is not UUID | Bad `X-Workspace-Id` |
| RATE_LIMIT_EXCEEDED | 429 | api-gateway | Request bucket exhausted | Too many auth requests |
| ROUTE_UNAVAILABLE | 503 | api-gateway | Downstream route unavailable | Service connection refused |
| MISSING_USER_CONTEXT | 401 | workspace/content | User header missing | Direct call without `X-User-Id` |
| INTERNAL_TOKEN_REQUIRED | 401 | workspace-service | Internal API token missing or invalid | content-service calls `/internal/**` without token |
| WORKSPACE_NOT_FOUND | 404 | workspace-service | Workspace not found | Unknown workspace id |
| WORKSPACE_ACCESS_DENIED | 403 | workspace-service | Workspace permission denied | MEMBER updates owner role |
| LAST_OWNER_CANNOT_BE_REMOVED | 409 | workspace-service | Owner safety violation | Remove final OWNER |
| LAST_OWNER_CANNOT_BE_CHANGED | 409 | workspace-service | Owner safety violation | Downgrade final OWNER |
| DUPLICATE_WORKSPACE_SLUG | 409 | workspace-service | Slug already exists | Manual duplicate slug |
| PERSONAL_WORKSPACE_ALREADY_EXISTS | 409 | workspace-service | User already has personal workspace | Second personal workspace |
| DUPLICATE_TAG | 409 | workspace-service | Tag duplicate | Same name/scope in workspace |
| NOTEBOOK_NOT_FOUND | 404 | workspace-service | Notebook not found | Internal permission lookup for unknown notebook |
| INVALID_INVITATION_TOKEN | 400 | workspace-service | Invite token invalid | Unknown token |
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
