# Offline background sync design (Faz 74-75)

Faz 74 adds a **design + frontend foundation** and Faz 75 turns it into a guarded **foreground MVP** for offline background sync on top of Faz 68-70 and Faz 71-73.

## Scope

- App-level **foreground** background sync triggers (no service worker in this phase).
- Eligibility policy utility for selecting safe drafts.
- Trigger policy utility for online/session/encryption/network guardrails.
- Feature flags, settings UI foundation, local diagnostics and summary model.
- Unit tests for policy/service behavior.

Out of scope:

- Production auto-sync rollout by default.
- Service Worker Background Sync / Periodic Sync implementation.
- Silent conflict resolution, CRDT/OT, offline comments/search sync.
- New backend service or backend API changes.

## Why app-level foreground only

Current sync depends on app session context:

- Authenticated user/session state.
- Memory-only offline encryption key availability.
- Existing conflict UI flow and explicit user actions.

Because these constraints are app-context dependent, Faz 74 keeps background sync in foreground runtime utilities and postpones service worker orchestration.

## Feature flags

- `FRONTEND_OFFLINE_BACKGROUND_SYNC_ENABLED` (default `false`)
- `FRONTEND_OFFLINE_BACKGROUND_SYNC_MODE`: `disabled | prompt | auto_safe` (default `disabled`)
- `FRONTEND_OFFLINE_BACKGROUND_SYNC_MAX_BATCH` (default `5`)
- `FRONTEND_OFFLINE_BACKGROUND_SYNC_MIN_INTERVAL_SECONDS` (default `60`)
- `FRONTEND_OFFLINE_BACKGROUND_SYNC_REQUIRE_UNMETERED` (default `false`)
- `FRONTEND_OFFLINE_BACKGROUND_SYNC_REQUIRE_CHARGING` (default `false`, reserved for future use)

## Trigger policy (Faz 75 MVP)

Background sync run is allowed only when:

- feature flag enabled and mode is not `disabled`
- browser is online
- user is authenticated
- encryption key/session requirements are satisfied
- minimum interval since last background run has passed
- if `requireUnmetered=true`, `navigator.connection.saveData` is not active

Foreground trigger sources:

- app boot when session/auth is available
- `window` online event
- app focus/foreground event
- settings offline section open
- manual "Run foreground background sync" action

Mode behavior:

- `disabled`: no background sync.
- `prompt`: returns `needsUserConsent=true` and does not auto-run.
- `auto_safe`: runs safe eligible batch.

## Eligibility policy

Eligible drafts:

- status `DRAFT` or `QUEUED`
- `baseEtag` present
- attempt count below max attempts
- within max draft age window
- no review-blocking errors

Skipped drafts include:

- `CONFLICT`, `FAILED`, `SYNCING`, `SYNCED`
- missing `baseEtag`
- attempt cap reached
- stale drafts
- rows requiring explicit user review

## Sync behavior (Faz 75)

- Reuses existing `syncOfflineDraft()` path.
- Sequential batch only (no parallel sync in MVP).
- No automatic merge apply on conflict.
- `conflict` results stay conflict-driven and user-facing.
- Session/auth-related failures can stop current batch early.
- Produces local summary object:
  - `attempted`, `synced`, `conflicts`, `failed`, `queued`, `skipped`
  - `startedAt`, `completedAt`, `needsUserConsent`, `stopReason`

## UX foundation (Faz 75)

- Settings offline section now shows background mode and diagnostics.
- Runtime mode can be overridden by local user preference (localStorage).
- Prompt mode: banner asks consent before batch run (`Sync now`, `Review drafts`, `Not now`).
- Auto-safe mode: non-intrusive syncing/summary banners on foreground app shell.
- Active note guardrail: currently edited note draft is skipped (`currently_editing`).
- Manual sync actions remain unchanged.

## Privacy and observability

- No raw note content in diagnostics.
- No user-level or note-title telemetry.
- Local-only diagnostics fields:
  - `lastBackgroundSyncStartedAt` / `lastBackgroundSyncCompletedAt`
  - mode, attempted/synced/conflicts/failed/queued/skipped
  - skipped reasons map and stopped reason
  - `lastBackgroundSyncResult` (aggregated JSON summary)

## Rollout / rollback

Rollout:

1. Keep prod `OFFLINE_BACKGROUND_SYNC_ENABLED=false`.
2. Enable in dev/staging with `prompt`, validate guardrails.
3. Optionally test `auto_safe` in staging with small batch/interval.

Rollback:

- Set mode to `disabled` or disable feature flag.
- Existing manual sync remains fully available.

## Faz 96 Service Worker Background Sync research/POC

Faz 96 keeps this foreground design as the primary sync path and adds a separate Service Worker
Background Sync research + dry-run POC foundation.

- Production Service Worker sync stays disabled.
- New flags default to safe values:
  - `FRONTEND_SW_BACKGROUND_SYNC_ENABLED=false`
  - `FRONTEND_SW_BACKGROUND_SYNC_DRY_RUN_ONLY=true`
  - `FRONTEND_SW_BACKGROUND_SYNC_REGISTER_ENABLED=false`
  - `FRONTEND_SW_BACKGROUND_SYNC_MAX_BATCH=3`
  - `FRONTEND_SW_BACKGROUND_SYNC_REQUIRE_ENCRYPTION_KEY=false`
- Dry-run counts eligible/skipped drafts and writes aggregate diagnostics only.
- Remote PATCH from the service worker requires a future explicit approval.
- Encrypted locked drafts are skipped with `encrypted_key_unavailable`.

See [`service-worker-background-sync-research.md`](service-worker-background-sync-research.md) and
[`service-worker-background-sync-design.md`](service-worker-background-sync-design.md).
