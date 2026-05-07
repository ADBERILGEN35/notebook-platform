# Search Permission Snapshot (Faz 34)

## Model

Faz 34 adopts a hybrid permission snapshot model:

- `workspaceReadable=true` and `restricted=false` documents can pass after workspace membership check.
- `restricted=true` documents require runtime notebook permission verification.
- Snapshot fields persisted in `search_documents`:
  - `visibilityMode` (`WORKSPACE` or `RESTRICTED`)
  - `permissionVersion` (nullable)
  - `workspaceReadable` (boolean)
  - `restricted` (boolean)
  - `permissionIndexedAt` (nullable timestamp)

This avoids full user-level ACL lists in the index while remaining fail-closed.

## Snapshot Contract

workspace-service internal endpoint:

- `GET /internal/notebooks/{notebookId}/search-permission-snapshot`
- scope: `internal:workspace:permission:read`
- audience: `workspace-service`

Current MVP visibility derivation:

- if notebook has explicit notebook members, snapshot is `RESTRICTED`.
- otherwise snapshot is `WORKSPACE`.

## Indexing Fallback

When snapshot fetch fails in search indexing:

- `restricted=true`
- `workspaceReadable=false`
- `permissionVersion=null`
- `permissionIndexedAt=null`

This is a conservative fallback and avoids fail-open exposure.

## Query Filtering

Search query flow:

1. check workspace membership (`/internal/workspaces/{workspaceId}/permissions`).
2. return empty page if user is not a member.
3. for unrestricted docs: accept directly.
4. for restricted docs: call `/internal/notebooks/{notebookId}/permissions`.
5. on permission API failures: fail-closed for restricted candidates.

## Refresh / Invalidation

workspace-service triggers:

- `POST /internal/search/permissions/notebooks/{notebookId}/refresh`
- scope: `internal:search:permission:write`
- audience: `search-service`

Search refresh behavior:

- fetch latest snapshot
- update permission projection for all indexed docs of notebook
- re-project to active search provider
- on snapshot fetch failure: no-op + metric/audit (mutation does not fail)

## Known Risks

- permission snapshot is eventually consistent.
- workspace member changes are not fully versioned into all notebook snapshots in this phase.
- OpenSearch total still reflects candidate totals, not exact authorized totals.

