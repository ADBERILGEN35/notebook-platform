# Pagination Design

Faz 19 implements pagination for current workspace-service and content-service list endpoints.
Changing array responses to enveloped responses is a deliberate breaking API change made before a
frontend is released.

## Candidate Endpoints

- `GET /workspaces`
- `GET /workspaces/{workspaceId}/notebooks`
- `GET /workspaces/{workspaceId}/tags`
- `GET /notebooks/{notebookId}/notes`
- `GET /notes/{noteId}/comments`
- `GET /notes/{noteId}/versions`
- `GET /workspaces/{workspaceId}/invitations`
- search endpoint

## Options

### Page/Size/Sort

Pros:

- Simple Spring Data support.
- Easy for admin/backoffice style navigation.
- Supports total counts.

Cons:

- Offset gets slower on large mutable lists.
- Concurrent inserts can move records between pages.

### Cursor

Pros:

- Better for high-growth feeds such as notes and comments.
- More stable under concurrent writes.

Cons:

- More complex response and index design.
- Harder to jump to arbitrary pages.
- Total counts are expensive or omitted.

## Recommendation

Use `page`, `size` and `sort` for MVP because most current list endpoints are workspace-scoped.
Plan cursor pagination later for notes, comments and search if load tests or product usage show high
growth.

Request parameters:

- `page`: zero-based page, default `0`
- `size`: default `20`, max `100`
- `sort`: allow-listed fields only, format `field,asc|desc`

Response:

```json
{
  "items": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0,
  "hasNext": false
}
```

## Breaking Change Risk

Existing clients previously received raw arrays from list endpoints. Faz 19 returns `PageResponse`
envelopes directly:

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

Defaults:

- `page=0`
- `size=20`
- max `size=100`

Validation errors:

- `INVALID_PAGE_REQUEST`
- `INVALID_PAGE_SIZE`
- `INVALID_SORT_FIELD`
- `INVALID_SORT_DIRECTION`

## Sort Whitelists

Workspace-service:

- workspaces: `name`, `createdAt`, `updatedAt`
- workspace members: `createdAt`, `updatedAt`, `joinedAt`, `role`
- notebooks: `name`, `createdAt`, `updatedAt`
- notebook members: `createdAt`, `updatedAt`, `role`
- tags: `name`, `createdAt`
- invitations: `createdAt`, `expiresAt`, `email`

Content-service:

- notes: `title`, `createdAt`, `updatedAt`
- note versions: `versionNumber`, `createdAt`
- comments: `createdAt`, `updatedAt`
- note tags: `createdAt`
- note links/backlinks: `createdAt`
- search: `updatedAt`, `createdAt`

Search pagination currently uses title matching for pageable queries. Full text ranking/cursor or
`search_after` style pagination remains future work.
