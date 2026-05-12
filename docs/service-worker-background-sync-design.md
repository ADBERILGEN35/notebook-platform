# Service Worker Background Sync Design (Faz 96)

Faz 96 adds a guarded Service Worker Background Sync POC foundation. The POC is dry-run only and
does not send remote writes from the service worker.

## Runtime flags

Production defaults:

| Flag | Default | Purpose |
|------|---------|---------|
| `FRONTEND_SW_BACKGROUND_SYNC_ENABLED` | `false` | Master switch for the POC surface. |
| `FRONTEND_SW_BACKGROUND_SYNC_DRY_RUN_ONLY` | `true` | Keeps worker behavior read/diagnostic-only. |
| `FRONTEND_SW_BACKGROUND_SYNC_REGISTER_ENABLED` | `false` | Allows registering the `offline-draft-sync` tag only when explicitly enabled. |
| `FRONTEND_SW_BACKGROUND_SYNC_MAX_BATCH` | `3` | Maximum dry-run eligible count. |
| `FRONTEND_SW_BACKGROUND_SYNC_REQUIRE_ENCRYPTION_KEY` | `false` | Reserved guardrail; encrypted locked drafts are still skipped. |

## Components

- `frontend/src/features/offline/sw-background-sync-types.ts` defines the tag, summary and skip reasons.
- `frontend/src/features/offline/sw-background-sync-policy.ts` feature-detects browser support and
  selects eligible drafts.
- `frontend/src/features/offline/sw-background-sync-registration.ts` registers the sync tag only when
  both master and registration flags are enabled, and exposes a manual dry-run helper.
- `frontend/src/features/offline/sw-background-sync-diagnostics.ts` writes the latest aggregate
  summary to IndexedDB.
- `frontend/src/service-worker/offline-sync-worker.ts` is an isolated worker listener foundation for
  the `offline-draft-sync` tag. The current VitePWA worker remains production-safe; the POC does not
  wire remote write behavior.

## Eligibility

Eligible for dry-run consideration:

- status `DRAFT` or `QUEUED`
- `baseEtag` present
- `attemptCount < maxAttempts`
- not locked/encrypted without key
- browser and network support checks pass
- session/CSRF checks are not known unavailable

Skip reasons:

- `conflict`
- `failed`
- `locked`
- `encrypted_key_unavailable`
- `missing_base_etag`
- `max_attempts`
- `session_unavailable`
- `csrf_unavailable`
- `currently_editing_unknown`
- `browser_unsupported`
- `status_not_eligible`
- `batch_limit`
- `network_unavailable`

## Dry-run diagnostics

The dry-run writes only aggregate counters:

```json
{
  "startedAt": "2026-05-12T00:00:00.000Z",
  "mode": "dry-run",
  "eligible": 2,
  "skipped": 3,
  "skipReasons": {
    "encrypted_key_unavailable": 1,
    "conflict": 1,
    "failed": 1
  }
}
```

No note title, note content, raw payload, bearer token, cookie value, CSRF token or encryption key is
stored in diagnostics.

## Future remote-write requirements

Remote write from a service worker requires a separate explicit decision and implementation:

- cookie/session behavior must be validated under real auth transport
- CSRF header/cookie interaction must be worker-safe
- `401` handling must coordinate with app session clearing
- `409`/`412` must mark the draft `CONFLICT` and defer review to UI
- an IndexedDB lock/lease must prevent races with foreground sync
- retry/backoff must be bounded and observable

Until those are implemented, foreground sync is the primary sync path.

## Multi-tab and foreground coordination

Faz 96 creates an `offline_sync_locks` store for the future lease shape:

- `draftId`
- `owner`: `foreground` or `service-worker`
- `expiresAt`

Because Faz 96 does not perform remote writes from the worker, the lock is not used for mutation
control yet. A future remote-write MVP must acquire a lease before changing draft status or sending
PATCH.

## Rollout policy

- Keep `FRONTEND_SW_BACKGROUND_SYNC_ENABLED=false` in production.
- Keep `FRONTEND_SW_BACKGROUND_SYNC_REGISTER_ENABLED=false` in all default Helm/GitOps values.
- Use Settings diagnostics and unit tests for POC validation.
- Do not enable silent conflict resolution, Periodic Sync, Push-triggered sync or passphrase unlock
  as part of this design.
