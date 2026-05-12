# Service Worker Background Sync Research (Faz 96)

Faz 96 evaluates Service Worker Background Sync as a future offline draft replay mechanism. It does
not roll out production Service Worker writes.

## Browser support matrix

| Platform | Status | Decision |
|----------|--------|----------|
| Chromium desktop / Edge / Opera | Supported for one-off Background Sync behind Service Worker + secure context constraints. | Can be used only behind runtime flags and feature detection. |
| Android Chrome / Chromium Android | Supported, but event timing is browser-managed and affected by OS/network/battery policy. | Treat as opportunistic; foreground sync remains primary. |
| Safari macOS | Not supported. | Must fall back to foreground sync on app open/focus/online. |
| iOS / iPadOS Safari and WebKit browsers | Not supported. | No Service Worker Background Sync rollout path. |
| Firefox desktop / Android | Not supported in current compatibility data. | Must fall back to foreground sync. |

References checked during Faz 96:

- MDN marks Background Synchronization and `ServiceWorkerRegistration.sync` as limited availability,
  secure-context-only APIs that run inside service workers.
- Can I Use currently shows Chromium-family support and no Safari/iOS/Firefox support.
- Chrome documentation positions Background Sync as a deferral mechanism for work after stable
  connectivity, with browser-controlled retries.

## Platform constraints

- Service workers have short, browser-managed lifetimes; long or interactive conflict resolution is
  not appropriate inside the worker.
- `sync` events are opportunistic. They are not a guaranteed job scheduler and can be delayed,
  suppressed, or retried according to browser policy.
- IndexedDB is available from service workers, but schema upgrades and blocked tabs can delay access.
- Background Sync requires secure context and an installed/active service worker registration.
- Battery/data saver/network policies can suppress or delay events. The app must not depend on the
  event for user-visible correctness.

## Auth, session and CSRF findings

- Cookie auth can be sent by service worker `fetch`, but session expiry is hard to present because
  the worker cannot show the app's login flow.
- CSRF protection needs an explicit future design: either a readable CSRF cookie/header model that
  works in the worker, or a worker-safe token handoff with strict lifetime and clearing semantics.
- A worker receiving `401` cannot safely call the frontend auth store or wipe session state in the
  same way as the app shell.
- Bearer-token mode is not suitable for a worker replay design unless a future secure token handoff
  is explicitly approved. Faz 96 does not add one.

## Encryption key constraint

Faz 69 stores the offline draft encryption key in memory only. A service worker is a separate
execution context and may start after the page has closed or after a browser restart.

Decision:

- The worker must not persist encryption keys in localStorage, sessionStorage, IndexedDB or Cache
  Storage.
- Encrypted/locked drafts are skipped with `encrypted_key_unavailable`.
- Passphrase unlock and key wrapping are out of scope.
- Foreground sync remains the secure path for encrypted drafts because it has app session/key context.

## Risk matrix

| Risk | Impact | Faz 96 decision |
|------|--------|-----------------|
| Browser support gaps | App-closed replay works only for part of users. | Feature-detect and keep foreground fallback. |
| Missing memory-only encryption key | Worker cannot decrypt safe payloads. | Skip encrypted locked drafts. |
| Session/CSRF unavailable | Worker write can fail or violate CSRF model. | Dry-run only; no remote PATCH. |
| Multi-tab/foreground race | Same draft could be processed twice. | Document lock store; no remote writes until lease is implemented. |
| Conflict response | Worker cannot ask user to review merge. | No silent merge; future 409/412 must mark `CONFLICT`. |
| Retry storm | Browser retry plus app foreground sync can amplify attempts. | Batch cap defaults to 3; dry-run only. |
| Privacy leakage | Diagnostics could expose titles/content. | Store counters and skip reasons only. |

## Decision

Service Worker Background Sync is feasible only as an opportunistic enhancement for a future phase.
It is not safe as the primary offline draft sync mechanism today. Faz 96 keeps production disabled
and ships only research, design, dry-run diagnostics and registration foundation.
