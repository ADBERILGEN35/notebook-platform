# API Contract Freeze

This document captures the backend contract before frontend work starts.

## Headers

- Public auth endpoints do not require `Authorization`.
- Protected gateway routes require `Authorization: Bearer <accessToken>`.
- Gateway strips client-provided identity headers and forwards:
  - `X-User-Id`
  - `X-User-Email`
  - `X-Workspace-Id` when valid and present
- Internal workspace endpoints are not routed through the gateway and may require `X-Internal-Token`.

## Strict Workspace Header Rollout

`X-Workspace-Id` remains optional by default for backwards compatibility.

When `APP_RLS_STRICT_WORKSPACE_HEADER=true`, tenant-scoped aggregate endpoints that only carry
`noteId`, `notebookId`, `commentId`, `tagId` or `invitationId` require `X-Workspace-Id`:

- missing header: `400 MISSING_WORKSPACE_CONTEXT`
- header conflicts with resolved aggregate workspace: `400 INVALID_WORKSPACE_CONTEXT`
- correct header: existing authorization and response behavior is preserved

Workspace path/query endpoints such as `/workspaces/{workspaceId}/...` and
`/notes/search?workspaceId=...` do not require the header because the tenant is known at request
entry. This is a staged RLS hardening contract, not a full URL redesign.

## Error Response

All services use:

```json
{
  "timestamp": "2026-04-30T12:00:00Z",
  "status": 400,
  "errorCode": "VALIDATION_ERROR",
  "message": "name: must not be blank",
  "path": "/workspaces",
  "requestId": "11111111-1111-1111-1111-111111111111",
  "fieldErrors": [{"field": "name", "message": "must not be blank"}]
}
```

Gateway error responses may omit `fieldErrors` when no validation field exists.

## Public Endpoints

Identity:

- `POST /auth/signup`
- `POST /auth/login`
- `POST /auth/refresh`

Workspace:

- `POST /workspaces`
- `GET /workspaces`
- `GET /workspaces/{workspaceId}`
- `PATCH /workspaces/{workspaceId}`
- `DELETE /workspaces/{workspaceId}`
- `GET /workspaces/{workspaceId}/members`
- `PATCH /workspaces/{workspaceId}/members/{userId}/role`
- `DELETE /workspaces/{workspaceId}/members/{userId}`
- `POST /workspaces/{workspaceId}/notebooks`
- `GET /workspaces/{workspaceId}/notebooks`
- `GET /notebooks/{notebookId}`
- `PATCH /notebooks/{notebookId}`
- `DELETE /notebooks/{notebookId}`
- `GET /notebooks/{notebookId}/members`
- `PUT /notebooks/{notebookId}/members/{userId}`
- `PATCH /notebooks/{notebookId}/members/{userId}/role`
- `DELETE /notebooks/{notebookId}/members/{userId}`
- `POST /workspaces/{workspaceId}/tags`
- `GET /workspaces/{workspaceId}/tags`
- `PATCH /tags/{tagId}`
- `DELETE /tags/{tagId}`
- `POST /workspaces/{workspaceId}/invitations`
- `GET /workspaces/{workspaceId}/invitations`
- `POST /invitations/accept`
- `POST /invitations/{invitationId}/revoke`

Content:

- `POST /notebooks/{notebookId}/notes`
- `GET /notes/{noteId}`
- `GET /notebooks/{notebookId}/notes`
- `PATCH /notes/{noteId}`
- `DELETE /notes/{noteId}`
- `GET /notes/{noteId}/versions`
- `GET /notes/{noteId}/versions/{versionNumber}`
- `POST /notes/{noteId}/restore/{versionNumber}`
- `GET /notes/{noteId}/links/outgoing`
- `GET /notes/{noteId}/links/incoming`
- `GET /notes/{noteId}/backlinks`
- `POST /notes/{noteId}/comments`
- `GET /notes/{noteId}/comments`
- `PATCH /comments/{commentId}`
- `DELETE /comments/{commentId}`
- `POST /comments/{commentId}/resolve`
- `POST /comments/{commentId}/reopen`
- `PUT /notes/{noteId}/tags/{tagId}`
- `DELETE /notes/{noteId}/tags/{tagId}`
- `GET /notes/{noteId}/tags`
- `GET /notes/search?workspaceId={workspaceId}&q={query}`

## Internal Endpoints

- `GET /internal/notebooks/{notebookId}/permissions?userId={userId}`
- `GET /internal/workspaces/{workspaceId}/tags/{tagId}/exists?scope=NOTE`

## Idempotency

- Safe/idempotent: `GET`, most `DELETE` soft archive/delete endpoints from client perspective.
- Upsert-like: `PUT /notebooks/{notebookId}/members/{userId}` and note tag attach may return conflict for duplicates.
- Non-idempotent: signup, login, refresh rotation, invitation create, note create, comment create.

## Pagination

Pagination is not implemented yet. List endpoints currently return arrays. This is a frontend-facing TODO before high-volume production usage.

## Versioning Strategy

MVP uses URI-stable unversioned endpoints. Breaking changes should either:

- add backwards-compatible fields only, or
- introduce `/v2` routes before frontend release.

## Contract TODOs

- Add pagination to list endpoints.
- Decide consistent response shape for deletes: empty `204` is current behavior.
- Add idempotency keys for note/comment create if clients need retry-safe writes.
