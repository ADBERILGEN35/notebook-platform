# Offline edit / sync design for notes (Faz 67)

This document specifies how **offline editing** and **sync** should work on top of:

- **Faz 59** — PWA + offline **read-only** note cache (`IndexedDB`, opened notes only).
- **Faz 39** — `ETag` + `If-Match` optimistic concurrency on `PATCH /notes/{noteId}`.
- **Faz 66** — Three-way **base / local / remote** model and **client-side** suggested merge (no silent merge).

Faz 67 is **design + minimal frontend foundation** only. It does **not** turn on production offline editing or background sync by default.

## Goals

1. **Deterministic** sync: one **snapshot** per note to push (not an operation log in this phase).
2. **Safe conflicts**: `412 NOTE_CONFLICT` → fetch remote → `analyzeNoteConflict(base, local, remote)` → user resolves; **no** silent apply.
3. **Clear UX state machine** for online/offline/dirty/sync/conflict.
4. **Security/privacy** documented: drafts are sensitive local state; logout wipes cache; no draft telemetry.

## Non-goals (explicit)

- CRDT/OT, realtime/WebSocket collaboration.
- Backend semantic merge endpoint or version compaction.
- Production background sync worker, multi-note batch sync, offline comments/search/attachments.
- Client-side encryption of drafts (future work).

## Feature flags (default off in production)

| Env / runtime key | Purpose | Default |
|-------------------|---------|---------|
| `FRONTEND_OFFLINE_NOTES_ENABLED` | Offline read cache | `true` in many samples; prod may be `false` |
| `FRONTEND_OFFLINE_EDIT_ENABLED` | Local draft persistence + future editable offline UX | **`false`** |
| `FRONTEND_OFFLINE_SYNC_ENABLED` | Reserved for automatic sync after reconnect | **`false`** (no worker in Faz 67) |
| `FRONTEND_OFFLINE_EDIT_MAX_DRAFTS` | Prune oldest drafts by `lastEditedAt` | `50` |
| `FRONTEND_OFFLINE_EDIT_MAX_DRAFT_AGE_DAYS` | Prune drafts older than this | `7` |

Runtime injection follows existing `FRONTEND_*` → `window.__NOTEBOOK_CONFIG__` pattern (see `frontend/docker-entrypoint.sh`).

## Why snapshot-based sync (not operation queue)

- BlockNote content is already **structured JSON** (`contentBlocks`).
- **ETag** on the server identifies the **whole note revision**; the client can send **one PATCH** with the full intended state from `localSnapshot`.
- An **operation-based** queue adds ordering, compaction, and replay complexity without a CRDT/OT layer; defer to future work if needed.

## IndexedDB: `offline_note_drafts`

Database: `notebook-offline` (version **2** adds the drafts store alongside existing `notes`).

Store: `offline_note_drafts`  
Key: **`noteId`** (at most **one active draft row per note**).

| Field | Type | Description |
|-------|------|-------------|
| `draftId` | UUID | Stable id for the row lifecycle |
| `noteId` | UUID | Primary key |
| `workspaceId`, `notebookId` | UUID | Scope metadata |
| `baseEtag` | string \| null | Server revision the draft was based on (for `If-Match`) |
| `baseUpdatedAt` | ISO string | Server `updatedAt` when base was captured |
| `baseSnapshot` | `{ title, contentBlocks }` | Last known server-aligned content for three-way merge |
| `localSnapshot` | `{ title, contentBlocks }` | Current offline edits |
| `status` | enum | See below |
| `lastEditedAt` | ISO string | Local activity; used for pruning |
| `queuedAt` | ISO \| null | When marked for sync |
| `syncedAt` | ISO \| null | Last successful sync timestamp |
| `conflictReason` | string \| null | Last conflict code/message |
| `attemptCount` | number | Sync attempts |
| `lastError` | string \| null | Last failure message (no remote logging) |

### Status enum

- `DRAFT` — local edits not yet queued.
- `QUEUED` — ready to sync when online (or retry after transient failure).
- `SYNCING` — transient; optional for UI.
- `SYNCED` — matches server until next local edit.
- `CONFLICT` — needs user resolution (Faz 66 dialog + offline-specific actions).
- `FAILED` — hard failure (permission, deleted note, etc.).

### Pruning

After each save, apply **max count** and **max age** using `lastEditedAt`. Oldest drafts drop first. This bounds storage and reduces stale merge bases.

## Sync contract (client design)

1. **Prepare**: `baseEtag` from when the user went offline or last successful sync; `baseSnapshot` aligned with that etag; `localSnapshot` from editor.
2. **PATCH** ` /notes/{noteId}` with body from `localSnapshot` and header `If-Match: "<baseEtag>"` (or server rules for weak/strong ETag as today).
3. **200**: mark draft `SYNCED`, refresh offline **read** cache entry with new note + ETag.
4. **412 / 409**: set `CONFLICT`; **GET** latest note; run `analyzeNoteConflict(baseSnapshot, localSnapshot, remoteSnapshot)`; show **same conflict UX family** as Faz 66; **never** auto-apply merge.
5. **404**: `FAILED` — note deleted; offer discard draft / save copy.
6. **403 / 401**: `FAILED` — permission lost.
7. **503 / 502 / 504** or network: keep **`QUEUED`** for retry (no silent drop).

Automatic sync on reconnect is **out of scope** unless both flags are enabled in a future phase **and** a worker/scheduler is implemented.

## UX state machine (reference)

| State | Meaning |
|-------|---------|
| `online-clean` | Editor matches server |
| `online-dirty` | Unsaved edits while online |
| `offline-readonly` | Offline, edit flag **off** (Faz 59 behavior) |
| `offline-editing-draft` | Offline, edit flag **on**, writing `localSnapshot` |
| `queued-for-sync` | Draft waiting for PATCH |
| `syncing` | PATCH in flight |
| `synced` | Draft row `SYNCED` |
| `sync-conflict` | `CONFLICT` |
| `sync-failed` | `FAILED` |

UX copy (future): banner “You are offline. Changes are stored on this device.”; primary “Save offline draft”; reconnect CTA “Sync pending changes” (manual in Faz 67).

## Conflict integration with Faz 66

- Reuse **`NoteConflictResolutionDialog`** patterns: summaries, reload, copy, overwrite, suggested merge when `analyzeNoteConflict` allows.
- Offline-specific actions (copy in design): **Keep offline draft as copy**, **Discard local draft**, **Retry sync**, **Overwrite latest** (explicit confirm).
- **No** silent merge and **no** background apply.

## Security and privacy

| Risk | Mitigation |
|------|------------|
| Sensitive content in IndexedDB | Same origin as app; user education; disable offline features in strict enterprises |
| Shared device | Logout + “Clear offline data” in Settings |
| Session invalidation | **Faz 67 recommendation**: treat like logout for **local** policy — `clearSession` already deletes the offline DB; any future “hard” session reset should also wipe drafts (documented here; align product policy before enabling edit) |
| XSS | Draft bodies are readable to injected scripts; rely on CSP + XSS prevention; no extra exposure vs offline read cache except **newer unsynced** content |
| Telemetry | **No** draft content in logs/analytics |

Enterprise: disable with `FRONTEND_OFFLINE_EDIT_ENABLED=false` (and optionally `FRONTEND_OFFLINE_NOTES_ENABLED=false`).

## Code layout (Faz 67)

- `frontend/src/features/offline/offline-db.ts` — DB name/version, store names, upgrade.
- `frontend/src/features/offline/offline-note-drafts.ts` — draft CRUD, prune, clear.
- `frontend/src/features/offline/offline-sync-types.ts` — types + UX state enum.
- `frontend/src/features/offline/offline-sync-policy.ts` — HTTP error → draft status mapping.
- `frontend/src/features/offline/offline-sync-design.test.ts` — unit tests.

## Rollout plan

1. Ship flags **off** in production; keep read-only offline as today.
2. Enable edit flag only in **dev/staging**; validate draft lifecycle + conflict UX manually.
3. Implement **manual** “Sync pending” before any automatic background sync.
4. Later: optional encryption, op-log, or backend merge **only** if product requires it.

## Why not CRDT yet

CRDT/OT requires either a replicated structure in the editor or a server merge contract. This platform already has **ETag snapshot** semantics and a **conservative** three-way merge for conflicts; layering CRDT before that need is proven would add operational and testing burden without matching the current API.
