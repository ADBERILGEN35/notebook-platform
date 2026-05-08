## Faz 53 contract notes

- Existing endpoint reused: `GET /admin/audit-events/export`.
- Scheduled export foundation adds **file-level contract**, not new HTTP endpoints:
  - JSONL archive payload
  - manifest JSON schema (versioned with `schemaVersion`)
  - SHA256 sidecar
## Faz 34 Internal Contract Additions

- `GET /internal/notebooks/{notebookId}/search-permission-snapshot`
- `GET /internal/workspaces/{workspaceId}/permissions?userId=...`
- `POST /internal/search/permissions/notebooks/{notebookId}/refresh`

These are internal service-to-service contracts only (not gateway-routed).

## Faz 35 Frontend Usage Note

Public frontend calls continue through `api-gateway` only (`http://localhost:8080`). Frontend does
not call backend services directly.

# API Contract Freeze

This document captures the backend contract before frontend work starts.

## Headers

- Public auth endpoints do not require `Authorization`.
- Protected gateway routes require `Authorization: Bearer <accessToken>`.
- Faz 41 ile gateway auth transport `bearer|cookie|dual` destekler.
- Cookie transportta access token `AUTH_ACCESS_COOKIE_NAME` cookie'den okunur.
- Gateway strips client-provided identity headers and forwards:
  - `X-User-Id`
  - `X-User-Email`
  - `X-Workspace-Id` when valid and present
- Internal workspace endpoints are not routed through the gateway and may require `X-Internal-Token`
  or `X-Service-Authorization: Bearer <service-jwt>` depending on `INTERNAL_AUTH_MODE`.
- Internal audit endpoints are not public gateway routes and require
  `X-Service-Authorization: Bearer <service-jwt>` with `internal:audit:read`.
- notification-service internal email endpoint is not routed by api-gateway and requires
  `X-Service-Authorization: Bearer <service-jwt>` with `internal:notification:email:send`.
- content-service search outbox ops endpoints are not public gateway routes and require
  `X-Service-Authorization: Bearer <service-jwt>` with
  `internal:content:search-outbox:read` or `internal:content:search-outbox:manage`.

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

Strict-mode workspace-service aggregate endpoints:

- `/notebooks/{notebookId}`
- `/notebooks/{notebookId}/members`
- `/notebooks/{notebookId}/members/{userId}`
- `/tags/{tagId}`
- `/notebooks/{notebookId}/tags/{tagId}`
- `/invitations/{invitationId}/revoke`

Strict-mode content-service aggregate endpoints:

- `/notebooks/{notebookId}/notes`
- `/notes/{noteId}`
- `/notes/{noteId}/versions`
- `/notes/{noteId}/links/outgoing`
- `/notes/{noteId}/links/incoming`
- `/notes/{noteId}/tags`
- `/notes/{noteId}/comments`
- `/comments/{commentId}`

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

List endpoints now return a paginated envelope instead of raw arrays. This is a Faz 19 breaking
change made before frontend release:

```json
{
  "items": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0,
  "hasNext": false,
  "hasPrevious": false
}
```

Pagination query parameters:

- `page`: default `0`
- `size`: default `20`, max `100`
- `sort`: `field,asc|desc`, endpoint-specific allow-list

Identity:

- `POST /auth/signup`
- `POST /auth/login`
- `POST /auth/refresh`
- `POST /auth/logout`
- `POST /auth/revoke-all`
- `GET /auth/me`

Auth cookie + csrf contract (Faz 41):

- cookie mode login/signup/refresh cevaplari auth cookie set eder
- `/auth/refresh` refresh tokeni body veya refresh cookie'den alabilir
- gateway unsafe methodlerde double-submit csrf uygular (cookie+header match)
- csrf error codes: `CSRF_TOKEN_REQUIRED`, `CSRF_TOKEN_INVALID`

Gateway admin audit proxy (Faz 43):

- `GET /admin/audit-events?source=identity|workspace|content&...filters` → server-mediated calls to each service `/internal/audit-events`.
- Requires authenticated platform-admin/allowlist authorization (`ADMIN_ACCESS_DENIED` on failure).
- Gateway signs per-target service JWT (`internal:audit:read`); service JWT is never returned to client.

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
- `GET /search/notes?workspaceId={workspaceId}&q={query}`

Content optimistic concurrency (Faz 39):

- `GET /notes/{noteId}` returns `ETag: "note-rev-{revision}"`.
- `PATCH /notes/{noteId}` and `POST /notes/{noteId}/restore/{versionNumber}` accept `If-Match`.
- stale `If-Match` -> `412 NOTE_CONFLICT`
- missing `If-Match` when strict config enabled (`CONTENT_REQUIRE_IF_MATCH_FOR_NOTE_UPDATE=true`)
  -> `428 PRECONDITION_REQUIRED`
- invalid `If-Match` format -> `400 INVALID_IF_MATCH_HEADER`

## Internal Endpoints

- `GET /internal/notebooks/{notebookId}/permissions?userId={userId}`
- `GET /internal/workspaces/{workspaceId}/tags/{tagId}/exists?scope=NOTE`
- `POST /internal/notifications/email`
- `GET /internal/email/suppressions`
- `POST /internal/email/suppressions`
- `POST /internal/email/suppressions/{id}/release`
- `POST /webhooks/email/{provider}`
- `POST /internal/search/documents`
- `DELETE /internal/search/documents/{noteId}`
- `GET /internal/search-index-outbox/status`
- `POST /internal/search-index-outbox/reprocess-failed`
- `GET /internal/search-index-source/notes`
- `POST /internal/search/reindex-jobs`
- `GET /internal/search/reindex-jobs/{jobId}`
- `POST /internal/search/reindex-jobs/{jobId}/cancel`
- `GET /internal/search/reindex-jobs/{jobId}/orphan-preview`

`POST /internal/search/reindex-jobs` accepts optional `cleanupOrphans` and `dryRunCleanup`.
Responses include
`totalArchivedOrphans`, `cleanupStartedAt`, `cleanupCompletedAt`, `cleanupOrphansRequested` and
`cleanupOrphansExecuted`, plus `dryRunCleanup`, `cleanupPreviewCount` and
`cleanupPreviewGeneratedAt`. `orphan-preview` returns scoped orphan identifiers and timestamps only;
title/content/query text are excluded.

`POST /webhooks/email/{provider}` is public-routable through api-gateway but authenticated by
provider signature, not user JWT. It accepts provider-specific JSON and returns an accepted event
count.

Suppression ops endpoints are internal-only and require service JWT scopes
`internal:notification:suppression:read` or `internal:notification:suppression:manage`.

## Idempotency

- Safe/idempotent: `GET`, most `DELETE` soft archive/delete endpoints from client perspective.
- Upsert-like: `PUT /notebooks/{notebookId}/members/{userId}` and note tag attach may return conflict for duplicates.
- Idempotent auth revoke: `POST /auth/logout` returns `204` even when the presented valid token is
  already revoked.
- Non-idempotent: signup, login, refresh rotation, invitation create, note create, comment create.

## Pagination

Pagination is implemented for workspace-service and content-service list endpoints. Cursor
pagination remains future work for high-volume notes/comments/search flows. With
`SEARCH_PROVIDER=opensearch`, `GET /search/notes` keeps the same response contract, but
`totalElements` is limited by provider candidate oversampling plus permission filtering until
permission snapshot indexing or cursor pagination is added.

## Versioning Strategy

MVP uses URI-stable unversioned endpoints. Breaking changes should either:

- add backwards-compatible fields only, or
- introduce `/v2` routes before frontend release.

## Contract TODOs

- Add pagination to list endpoints.
- Decide consistent response shape for deletes: empty `204` is current behavior.
- Add idempotency keys for note/comment create if clients need retry-safe writes.

## Faz 45 Notification Center contracts

Added contracts on existing gateway + notification-service:

- `GET /notifications`
- `GET /notifications/unread-count`
- `POST /notifications/{notificationId}/read`
- `POST /notifications/read-all`
- `POST /notifications/{notificationId}/archive`
- `POST /internal/notifications/in-app` (service JWT scope: `internal:notification:in-app:create`)

## Faz 47 Conflict UX compatibility

Faz 47 conflict resolution uses existing content-service contracts only:

- `GET /notes/{noteId}` (ETag baseline refresh)
- `PATCH /notes/{noteId}` with `If-Match` (overwrite/retry path)
- `POST /notebooks/{notebookId}/notes` (save local conflict copy)

No new backend endpoint is introduced for this phase.

## Faz 48 Audit export contracts

Gateway admin export contract:

- `GET /admin/audit-events/export?source=<identity|workspace|content>&format=<csv|jsonl>&createdFrom=<iso>&createdTo=<iso>[&filters...]`

This contract remains gateway-only; browser clients do not call internal `/internal/audit-events`
routes directly.

## Faz 49 Notification preferences contracts

- `GET /notification-preferences`
- `PATCH /notification-preferences`
- Internal create responses can include `status=SKIPPED` with `skippedReason=USER_PREFERENCE_DISABLED`.

## Faz 51 MFA / WebAuthn contracts

- `GET /auth/mfa/settings`
- `POST /auth/mfa/webauthn/registration/options`
- `POST /auth/mfa/webauthn/registration/verify`
- `POST /auth/mfa/webauthn/authentication/options`
- `POST /auth/mfa/webauthn/authentication/verify`
- `POST /auth/mfa/recovery-codes/generate`
- `POST /auth/mfa/recovery-codes/verify`
- `GET/PATCH/DELETE /auth/mfa/webauthn/credentials/{credentialId}`
- `POST /auth/login` can return:
  - `mfaRequired=true`
  - `mfaSessionId`
  - `availableMethods`
