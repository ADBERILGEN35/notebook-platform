# Frontend MVP (Faz 35)

## Goal

Provide a production-looking, minimal product shell that consumes existing backend APIs through
`api-gateway` (`http://localhost:8080`) without introducing new backend domains.

## Architecture

- `frontend/` Vite + React + TypeScript project
- route-based shell:
  - `/login`, `/signup`
  - `/app`, `/app/workspaces/:workspaceId`, `/app/notebooks/:notebookId`
  - `/app/notes/:noteId`, `/app/search`, `/app/settings`, `/app/settings/security`
- shared API client:
  - injects `Authorization` and `X-Workspace-Id`
  - attempts refresh on `401`
  - clears local session on refresh failure
  - parses backend `ErrorResponse` format

## Faz 37 Rich Editor Upgrade

- Textarea-based note editing was replaced with BlockNote-based editor integration.
- Serialization/deserialization guard layer preserves backend `contentBlocks` contract.
- Invalid or unknown block payloads fall back to safe empty document state without crashing UI.
- Save remains manual in this phase to avoid auto-save/version churn edge cases.

## Faz 38 Auto-save + Debounce + Conflict Awareness

- Note editor uses debounced auto-save for title/content updates.
- Save pipeline includes:
  - debounce scheduling
  - in-flight queueing
  - snapshot deduplication (no-op payloads skipped)
  - manual save override (`Save now`)
- Conflict handling baseline:
  - `409` / `412` responses move editor to `conflict` state
  - UI shows reload action
  - merge/overwrite UI is intentionally deferred
- Version restore resets save baseline to prevent immediate redundant autosave.
- More frequent autosave may increase backend note-version and search indexing event volume.

## Data Handling

- Page response handling supports:
  - `hasNext` / `hasPrevious` format
  - search-service variant with `last` fallback mapping
- UI error mapping for `400`, `401`, `403`, `404`, `429`, `503`
- request IDs can be read from backend error payloads

## UX Scope

- Sidebar (workspace + notebook navigation)
- Topbar (search input + create action + security shortcut)
- Main content area (workspace/notebook/note/search/settings views)
- Right panel tabs (comments, versions, info placeholder)

## Deferred Items

- Full BlockNote editor integration
- Real-time collaboration
- Notification center UI
- Advanced member management UI
- Frontend deployment packaging/Helm
- Block-level comments
- Real-time collaboration
- Offline mode
- Rich editor advanced slash command customization
- Conflict merge UX and backend optimistic concurrency contract (ETag/If-Match)

## Faz 39 Optimistic Concurrency Contract

- Note fetch uses `ETag` response header from backend.
- Note save (`PATCH`) and restore (`POST /restore`) send `If-Match` with latest known ETag.
- Save/restore responses refresh local ETag baseline.
- `412 NOTE_CONFLICT` and `428 PRECONDITION_REQUIRED` map to editor `conflict` state.
- Conflict banner action is `Reload latest`; merge/overwrite UX remains deferred.

E2E scope moved to [`docs/frontend-e2e.md`](frontend-e2e.md).

