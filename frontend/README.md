# Frontend MVP Foundation (Faz 35)

React + TypeScript + Vite frontend shell for the notebook platform.

## Stack

- React 19
- TypeScript
- Vite
- React Router
- TanStack Query
- Zustand
- Zod
- Tailwind CSS
- Vitest + Testing Library

## Local Run

```bash
cp .env.example .env
npm install
npm run dev
```

Default API base URL:

- `VITE_API_BASE_URL=http://localhost:8080`
- `VITE_AUTH_TRANSPORT=bearer|cookie|dual`

Runtime Docker/Kubernetes config:

- `FRONTEND_API_BASE_URL` -> served via `/runtime-config.js`
- Runtime value overrides `VITE_API_BASE_URL`

## Implemented MVP Flows

- Login + signup + token-based session state
- Access token auto refresh on `401`
- Workspace listing/selection + create workspace
- Notebook listing + create notebook
- Note list + BlockNote rich text edit/save
- Search page (`/search/notes`)
- Right panel comments + version history actions
- Settings security: logout and revoke-all

## BlockNote Editor

- Editor component: `src/features/notes/components/BlockNoteEditor.tsx`
- Serialization utilities: `src/features/notes/utils/blocknote-serialization.ts`
- Backend contract is preserved as `contentBlocks` JSON array with `id/type/props/content/children`.

## Auto-save (Faz 38)

- Debounced auto-save is enabled for note title + BlockNote content.
- Default debounce: `1500ms`
- Minimum change interval guard: `1000ms`
- Save state machine labels:
  - Saved
  - Unsaved changes
  - Saving...
  - Save failed. Retry
  - Conflict detected
- Manual save button remains available (`Save now`) and cancels pending debounce.
- Payload deduplication prevents sending identical snapshot repeatedly.
- Basic conflict awareness:
  - save `409` / `412` -> conflict state + reload action
  - no merge UI in this phase

## ETag / If-Match (Faz 39)

- `getNote` reads `ETag` response header and keeps it with note payload.
- `updateNote` and `restoreVersion` send `If-Match` when ETag is available.
- Successful save/restore stores returned new ETag baseline.
- `412` / `428` responses map to conflict UI and require reloading latest server note.

## Backend merge analysis flag (Faz 71)

- `FRONTEND_BACKEND_MERGE_ANALYSIS_ENABLED=false` by default.
- When enabled, conflict dialog first calls backend `POST /notes/{id}/merge/analyze`.
- If backend analysis fails, frontend automatically falls back to Faz 66 client merge analysis.

## Backend merge apply flag (Faz 72)

- `FRONTEND_BACKEND_MERGE_APPLY_ENABLED=false` by default.
- When enabled, "Apply suggested merge" can call backend `POST /notes/{id}/merge/apply`.
- If backend apply fails or is disabled, existing client-side save/PATCH conflict flow remains fallback.

## Merge analytics adapter (Faz 73)

- Frontend adds a privacy-safe `trackMergeEvent()` contract with a default no-op adapter.
- Conflict actions are instrumented with non-sensitive fields only (`source`, action, conflict count, backend usage booleans).
- No note content, block text, user ID, or note ID is tracked.

## Cookie Auth + CSRF (Faz 41)

- Cookie mode (`AUTH_TRANSPORT=cookie`) stores auth tokens in httpOnly cookies.
- Frontend does not need localStorage access/refresh tokens in cookie mode.
- API client sends `credentials: include`.
- Unsafe methods send `X-CSRF-Token` from `NP-XSRF-TOKEN` cookie.
- Bearer mode remains for backwards compatibility/dev.

## Admin / Audit Explorer (Faz 42)

- Routes: `/app/admin`, `/app/admin/audit`, `/app/admin/audit/:eventId`, `/app/admin/notifications/analytics` (Faz 81: `FRONTEND_NOTIFICATION_ANALYTICS_ENABLED`, permission `admin:notifications:analytics:read`), `/app/admin/notifications/dead-letter` (Faz 82: `FRONTEND_NOTIFICATION_DEAD_LETTER_ENABLED`, dead-letter read/requeue permissions), `/app/admin/notifications/retention` (Faz 83: `FRONTEND_NOTIFICATION_RETENTION_ENABLED`, optional `FRONTEND_NOTIFICATION_RETENTION_PURGE_ENABLED`, retention read/run permissions), `/app/admin/notifications/legal-holds` (Faz 84: `FRONTEND_NOTIFICATION_LEGAL_HOLD_ENABLED`, legal-hold read/write permissions), `/app/admin/enterprise` (+ security/integrations/change-requests when `FRONTEND_ENTERPRISE_ADMIN_WRITE_ENABLED`; approve/reject UI gated by `FRONTEND_ENTERPRISE_ADMIN_APPROVALS_ENABLED`, default on) (nested under authenticated shell).
- Gated behind `VITE_ADMIN_UI_ENABLED` / `ADMIN_UI_ENABLED` with optional trusted `ADMIN_UI_DEV_OPEN` for localhost-style sessions.
- `VITE_AUDIT_API_MODE` / `AUDIT_API_MODE` selects `mock` (default dev) vs `real` placeholder (`GET /admin/audit-events` once the gateway exposes it).
- See `docs/admin-audit-ui.md` for rollout guidance (service JWT never ships to browsers).
- **Faz 79:** `/auth/me` may include `platformRoles` / `platformPermissions`; `src/features/admin/access/admin-permissions.ts` gates nav and actions (backend still authoritative). See `docs/admin-rbac.md`.
- **Faz 88:** `FRONTEND_ADMIN_RBAC_OVERRIDES_STATUS_ENABLED` → `ADMIN_RBAC_OVERRIDES_STATUS_ENABLED` in `runtime-config.js` shows the read-only **GitOps RBAC overrides** card on `/app/admin/rbac` (no YAML upload). See `docs/admin-rbac-runtime-overrides.md`.

## Platform Admin Proxy (Faz 43)

- `GET /admin/audit-events` is now backed by gateway server-side proxying with admin authorization.
- Browser still sends only user auth cookie/bearer token; service JWT is generated and used only in gateway.
- `ADMIN_UI_DEV_OPEN` should be used for trusted local mock-mode only.

## CSP + Runtime Security (Faz 44)

- Runtime-configurable CSP via container envs:
  - `FRONTEND_CSP_ENABLED`
  - `FRONTEND_CSP_REPORT_ONLY`
  - `FRONTEND_CSP_REPORT_URI`
  - `FRONTEND_CSP_CONNECT_SRC` / `FRONTEND_CSP_IMG_SRC` / `FRONTEND_CSP_FONT_SRC`
- `runtime-config.js` stays as external script include to keep `script-src 'self'` compatible.
- Build sourcemap control via `VITE_SOURCEMAP=true|false` (prod recommended: `false`).

## Security Note

Tokens are stored in localStorage for MVP speed when using bearer mode.
Production should prefer HttpOnly cookie storage (already supported via `AUTH_TRANSPORT=cookie`).

## Known Limitations

- No real-time collaboration
- No offline edit queue
- No conflict merge UI
- Backend-side version compaction for autosave churn is not implemented
- Mobile polish is limited
- Invitation/member advanced UI is placeholder-level
- No block-level comments yet

## E2E (Faz 36)

Playwright tests validate critical user journeys end-to-end.

```bash
npx playwright install
npm run test:e2e
```

Other modes:

- `npm run test:e2e:headed`
- `npm run test:e2e:ui`
- `npm run test:e2e:debug`

Detailed E2E notes: `frontend/e2e/README.md`.

## Docker Deploy (Faz 40)

Build image:

```bash
bash scripts/frontend/docker-build.sh
```

Run container:

```bash
docker run --rm -p 3000:8080 \
  -e FRONTEND_API_BASE_URL=http://localhost:8080 \
  notebook-platform/frontend:local
```

Smoke check:

```bash
FRONTEND_BASE_URL=http://localhost:3000 bash scripts/smoke-test-frontend.sh
```

## Notification Center flag

Faz 45 Notification Center UI is controlled by runtime flag:

- `FRONTEND_NOTIFICATIONS_ENABLED=true|false`

When disabled, topbar bell and `/app/notifications` experience are hidden/blocked in UI.

## Workspace notification preferences (Faz 65)

- Runtime / Vite: `FRONTEND_WORKSPACE_NOTIFICATION_PREFERENCES_ENABLED` /
  `VITE_WORKSPACE_NOTIFICATION_PREFERENCES_ENABLED` (Settings → per-workspace channel overrides).
- Runtime / Vite: `FRONTEND_WORKSPACE_NOTIFICATION_POLICIES_ENABLED` /
  `VITE_WORKSPACE_NOTIFICATION_POLICIES_ENABLED` (Settings → workspace owner/admin notification policies).
- Backend feature `WORKSPACE_NOTIFICATION_PREFERENCES_ENABLED` must be on for preference API calls to succeed.
- Backend feature `WORKSPACE_NOTIFICATION_POLICIES_ENABLED` must be on for policy API calls to succeed.

## Realtime notification SSE (Faz 56)

- Runtime flag: `FRONTEND_NOTIFICATIONS_SSE_ENABLED=true|false`.
- SSE is enabled only in cookie/dual auth transport because browser EventSource does not support
  custom Authorization header.
- Bearer-only mode stays on polling fallback.
- Polling (`30s` unread count) is intentionally preserved for resilience and multi-pod consistency
  fallback.

## Responsive shell notes (Faz 46)

- App shell uses drawer navigation for mobile/tablet and persistent sidebar on desktop.
- Note page details panel (comments/versions/info) becomes a drawer on non-desktop.
- Responsive smoke spec: `e2e/responsive.spec.ts`.

## Conflict resolution UX (Faz 47)

- On `412/428` note save conflicts, editor shows `Review conflict` action.
- Conflict dialog offers:
  - reload latest (discard local)
  - save local changes as conflict copy
  - overwrite latest with explicit confirmation
- Overwrite path always re-fetches latest ETag before PATCH retry.
- No automatic merge/CRDT/OT is implemented in this phase.

## Conflict diff / suggested merge (Faz 66)

- Utilities: `src/features/notes/utils/blocknote-diff.ts`, `blocknote-merge.ts`.
- Three-way **base / local / remote** summaries in the conflict dialog; **Apply suggested merge** runs
  a conservative client merge then a normal `PATCH` with the latest ETag (`saveMergedAfterConflict`).
- See `docs/note-conflict-diff-merge.md`.

## Admin audit export (Faz 48)

- Admin audit page supports export format selection (`CSV`, `JSONL`) and download.
- Export requires `createdFrom` and `createdTo` filters.
- Backend gateway enforces export limits and redaction; frontend does not handle raw secret data.

## Notification preferences (Faz 49)

- `/app/settings/notifications` route is available through Settings.
- Mandatory security preferences are disabled in UI and cannot be toggled off.

## Digest / quiet hours (Faz 58)

- Settings page includes delivery schedule controls:
  - email digest enable/frequency
  - quiet hours start/end
  - timezone
- Security notifications remain immediate and are not delayed by digest/quiet hours.

## PWA / offline read-only (Faz 59)

- Feature flags:
  - `FRONTEND_PWA_ENABLED`
  - `FRONTEND_OFFLINE_NOTES_ENABLED`
  - `FRONTEND_OFFLINE_NOTES_MAX_ITEMS`
- Recently opened notes are cached in IndexedDB for read-only offline access.
- Logout/session clear removes cached notes.
- Offline mode disables note editing/save/comment/restore actions.

## Offline edit/sync foundation (Faz 67)

- Design: `docs/offline-edit-sync-design.md` (snapshot-based drafts; ETag/`If-Match`; Faz 66 merge on conflict).
- Additional flags (default **off** in production samples): `FRONTEND_OFFLINE_EDIT_ENABLED`,
  `FRONTEND_OFFLINE_SYNC_ENABLED`, `FRONTEND_OFFLINE_EDIT_MAX_DRAFTS`,
  `FRONTEND_OFFLINE_EDIT_MAX_DRAFT_AGE_DAYS`.
- Code: `src/features/offline/offline-db.ts` (IndexedDB v2 + `offline_note_drafts`),
  `offline-note-drafts.ts`, `offline-sync-policy.ts`, `offline-sync-types.ts`.
- No production background sync worker in this phase; NotePage offline editing remains gated on future work.

## Offline edit/sync MVP (Faz 68)

- `NotePage` offline cached notes can be edited when `FRONTEND_OFFLINE_EDIT_ENABLED=true`.
- Draft writes stay local (IndexedDB) and do not call backend while offline.
- Manual controls: `Save offline draft`, `Queue for sync`, `Sync now`.
- Manual sync requires `FRONTEND_OFFLINE_SYNC_ENABLED=true` and online status.
- Conflict resolution for offline draft sync reuses the Faz 66 conflict dialog flow.

## Offline encryption hardening (Faz 69)

- New flags:
  - `FRONTEND_OFFLINE_ENCRYPTION_ENABLED`
  - `FRONTEND_OFFLINE_DRAFT_ENCRYPTION_REQUIRED`
  - `FRONTEND_OFFLINE_CACHE_ENCRYPTION_ENABLED`
- Session-bound, memory-only WebCrypto key (AES-GCM) is used for encrypted offline payloads.
- If draft encryption is required but unavailable, offline edit is disabled and read-only fallback is used.

## Offline sync rollout hardening (Faz 70)

- Added flags:
  - `FRONTEND_OFFLINE_SYNC_ROLLOUT_MODE`
  - `FRONTEND_OFFLINE_SYNC_MAX_ATTEMPTS`
  - `FRONTEND_OFFLINE_SYNC_STALE_MINUTES`
- Sync mode remains flag-gated; production can keep sync disabled while piloting manual/guarded rollout.

## Offline foreground background sync foundation (Faz 74)

- New runtime flags:
  - `FRONTEND_OFFLINE_BACKGROUND_SYNC_ENABLED`
  - `FRONTEND_OFFLINE_BACKGROUND_SYNC_MODE` (`disabled | prompt | auto_safe`)
  - `FRONTEND_OFFLINE_BACKGROUND_SYNC_MAX_BATCH`
  - `FRONTEND_OFFLINE_BACKGROUND_SYNC_MIN_INTERVAL_SECONDS`
  - `FRONTEND_OFFLINE_BACKGROUND_SYNC_REQUIRE_UNMETERED`
  - `FRONTEND_OFFLINE_BACKGROUND_SYNC_REQUIRE_CHARGING` (reserved)
- Foundation is app-level foreground only (no service-worker production background sync in this phase).
- Existing manual sync flow remains intact; production default should stay `disabled`.

## Offline foreground background sync MVP (Faz 75)

- AppShell now evaluates background sync on foreground lifecycle triggers:
  - app boot (auth/session ready)
  - browser `online` event
  - app `focus` event
  - settings section open
  - manual run button
- `prompt` mode shows consent banner before running batch sync.
- `auto_safe` mode runs eligible drafts sequentially and shows syncing/summary banners.
- Current note being edited is skipped from background batch (`currently_editing` guardrail).

## Enterprise SSO login (Faz 60)

- Runtime flag: `FRONTEND_SSO_ENABLED`.
- Login page can render provider buttons from `GET /auth/sso/providers`.
- Provider action redirects browser to `/auth/sso/{provider}/authorize`.
- Password login flow is preserved.

## MFA UI + step-up (Faz 51)

- Feature flag: `FRONTEND_MFA_UI_ENABLED`.
- Settings/security supports passkey setup and recovery-code generation flows.
- Login page supports MFA step-up flow (`passkey` or `recovery code`) when backend returns
  `mfaRequired=true`.

## Admin MFA UX (Faz 52)

- Admin audit and **enterprise console** views handle `ADMIN_MFA_REQUIRED` with the same Security Settings CTA.
- CTA routes users to Settings/Security for passkey/recovery setup.
- Settings MFA panel shows remaining recovery-code count and admin-required banner.

## Scheduled export note (Faz 53)

- Admin UI keeps manual export.
- Scheduled export is operations-managed and documented in backend/ops docs (no new frontend scheduler UI).
