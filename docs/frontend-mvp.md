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

## Faz 40 Deployment Foundation

- Frontend now has production Docker image and nginx runtime.
- Runtime config is environment-driven (`FRONTEND_API_BASE_URL`) via `/runtime-config.js`.
- Helm/GitOps values include frontend deployment/service/ingress.
- Security headers and cache policy are applied at nginx layer.
- Separate-host ingress model is the primary recommendation:
  - app host -> frontend
  - api host -> api-gateway

E2E scope moved to [`docs/frontend-e2e.md`](frontend-e2e.md).

## Faz 41 Cookie Auth + CSRF

- Frontend auth transport now supports `bearer|cookie|dual`.
- Cookie mode uses `credentials: include` and does not require localStorage token state.
- Unsafe requests add `X-CSRF-Token` from `NP-XSRF-TOKEN` cookie.
- Session restore in cookie mode is handled with `GET /auth/me`.


## Faz 42 Admin / Audit UI Shell

- Routes: `/app/admin`, `/app/admin/audit`, `/app/admin/audit/:eventId`.
- Mock-backed audit explorer with URL-synced filters, paging, masked metadata drawer, deterministic fixtures for tests.
- Gated behind `ADMIN_UI_ENABLED` (and optional trusted `ADMIN_UI_DEV_OPEN` for local environments).
- See `docs/admin-audit-ui.md` for operational + security rollout notes.

## Faz 43 Admin Audit Real Mode

- `AUDIT_API_MODE=real` now targets gateway `GET /admin/audit-events`.
- Browser still never receives service JWT; gateway performs admin authorization and internal fan-out.
- UI maps `ADMIN_ACCESS_DENIED`, `ADMIN_AUDIT_DISABLED`, `AUDIT_SOURCE_UNAVAILABLE` to explicit states.

## Faz 44 CSP + Runtime Security

- Frontend Nginx security headers are generated at runtime with CSP mode toggles.
- CSP supports disabled / report-only / enforce rollouts via `FRONTEND_CSP_*` envs.
- `runtime-config.js` remains external script (no inline bootstrap script), compatible with
  `script-src 'self'`.

## Faz 45 Notification Center (Polling MVP)

- New topbar bell icon with unread badge and dropdown preview.
- Dedicated page: `/app/notifications`.
- Actions: mark as read, mark all as read, archive.
- Filters: unread-only, type, optional workspace, pagination.
- No realtime socket transport in this phase; polling-based refresh is used.

## Faz 46 Mobile / Responsive Polish

- App shell now has mobile/tablet drawer navigation and compact topbar behavior.
- Note page right panel shifts to drawer on non-desktop viewports.
- Search, notifications and admin/audit pages include small-screen usability updates.
- See [`frontend-responsive.md`](frontend-responsive.md).

## Faz 47 Conflict Resolution UX (No Auto-Merge)

- Conflict state now includes local snapshot and failure context from autosave.
- `Review conflict` opens a resolution dialog with:
  - latest server preview
  - local unsaved preview
  - actions: reload latest, save as copy, overwrite latest
- Overwrite flow re-fetches latest ETag and replays PATCH with explicit user confirmation.
- No automatic merge algorithm, CRDT/OT, or real-time collaboration in this phase.

## Faz 49 Notification Preferences

- Settings now exposes notification channel toggles.
- Mandatory security preferences are displayed disabled with explanatory text.

## Faz 50 MFA Foundation

- Settings/Security page includes MFA section with passkey/recovery placeholder actions.
- Browser capability detection is exposed via `isWebAuthnSupported()`.

