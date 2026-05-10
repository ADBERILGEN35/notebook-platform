# PWA / Offline Read Mode (Faz 59)

## Scope

This phase adds a limited PWA foundation and offline **read-only** note access:

- Web app manifest + service worker registration
- App shell/static asset cache
- Recently opened notes cache in IndexedDB
- Offline indicator and offline note read-only behavior
- Offline data clearing on logout/session clear

Not included in Faz 59:

- Offline editing queue
- Background sync/conflict sync/auto-merge

Faz 66 adds **online** conflict diff and optional client-suggested merge in the conflict dialog.
Faz 67 adds **design + IndexedDB draft foundation** for future offline edit/sync (flags default
**off**); it does **not** enable production offline editing or background sync. Until offline edit
is explicitly enabled, offline notes stay read-only when the network is unavailable (see
[`note-conflict-diff-merge.md`](note-conflict-diff-merge.md) and
[`offline-edit-sync-design.md`](offline-edit-sync-design.md)).
- Offline comment creation or notification actions
- Full offline search index

## Runtime Flags

- `FRONTEND_PWA_ENABLED` (default `true`)
- `FRONTEND_OFFLINE_NOTES_ENABLED` (default `true`)
- `FRONTEND_OFFLINE_NOTES_MAX_ITEMS` (default `50`)
- `FRONTEND_OFFLINE_EDIT_ENABLED` (default **`false`**) — experimental local drafts; see [`offline-edit-sync-design.md`](offline-edit-sync-design.md)
- `FRONTEND_OFFLINE_SYNC_ENABLED` (default **`false`**) — reserved; no production sync worker in Faz 67
- `FRONTEND_OFFLINE_EDIT_MAX_DRAFTS` / `FRONTEND_OFFLINE_EDIT_MAX_DRAFT_AGE_DAYS` — draft retention caps
- `FRONTEND_OFFLINE_ENCRYPTION_ENABLED` / `FRONTEND_OFFLINE_DRAFT_ENCRYPTION_REQUIRED` / `FRONTEND_OFFLINE_CACHE_ENCRYPTION_ENABLED` — offline storage hardening controls (Faz 69)

These are exposed through `/runtime-config.js` and consumed by frontend runtime feature checks.

## Data Model and Storage

Offline note cache lives in IndexedDB database `notebook-offline` and contains:

- `noteId`
- `note` payload (`title`, `contentBlocks`, `workspaceId`, `notebookId`, `updatedAt`, etc.)
- `etag`
- `cachedAt`

When offline edit flags allow it, a separate object store `offline_note_drafts` holds per-note draft
rows (`baseSnapshot`, `localSnapshot`, `baseEtag`, status, etc.); see
[`offline-edit-sync-design.md`](offline-edit-sync-design.md).

Cache policy:

- Only explicitly opened notes are cached
- Cache is pruned by max item count
- Logout/session clear deletes the entire offline DB (cached notes **and** drafts)

## Security and Privacy Notes

- Offline notes are persisted locally in browser storage (IndexedDB).
- Cached content is JavaScript-accessible by the same origin; this is expected for offline read support.
- Shared devices should use logout + "Clear cached notes".
- Security/admin/audit/export datasets are not cached by this phase.
- Sensitive deployments can disable offline notes with `FRONTEND_OFFLINE_NOTES_ENABLED=false`.
- Faz 69 adds optional at-rest encryption foundation for offline drafts/cache; see [`offline-data-encryption.md`](offline-data-encryption.md).

## UX Behavior

- Global offline banner appears when network is unavailable.
- If note API fails and a cached copy exists, note page opens in offline read-only mode.
- Faz 68 adds optional offline draft editing when `FRONTEND_OFFLINE_EDIT_ENABLED=true`; sync is still manual and gated by `FRONTEND_OFFLINE_SYNC_ENABLED`.
- Faz 70 adds rollout mode and sync hardening flags for production-safe staged rollout.
- Faz 74 adds app-level foreground background sync **design/foundation** behind dedicated flags; production default remains disabled and no service-worker background sync is shipped.
- Faz 75 adds foreground lifecycle-driven prompt/auto-safe MVP behavior while keeping background sync app-open only (no closed-app/background-worker execution).
- Offline mode disables:
  - editor writes (`readOnly`)
  - manual save and autosave writes
  - comment/restore actions
- If no cached copy exists, note page shows: "This note is not available offline."

## Deployment Notes

- `manifest.webmanifest` is served with short cache (`max-age=300`)
- `sw.js` is served with no-cache/no-store
- `/assets/*` remains immutable cache
- CSP keeps `worker-src 'self' blob:` and `manifest-src 'self'`
