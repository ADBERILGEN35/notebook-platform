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

## Cookie Auth + CSRF (Faz 41)

- Cookie mode (`AUTH_TRANSPORT=cookie`) stores auth tokens in httpOnly cookies.
- Frontend does not need localStorage access/refresh tokens in cookie mode.
- API client sends `credentials: include`.
- Unsafe methods send `X-CSRF-Token` from `NP-XSRF-TOKEN` cookie.
- Bearer mode remains for backwards compatibility/dev.

## Admin / Audit Explorer (Faz 42)

- Routes: `/app/admin`, `/app/admin/audit`, `/app/admin/audit/:eventId` (nested under authenticated shell).
- Gated behind `VITE_ADMIN_UI_ENABLED` / `ADMIN_UI_ENABLED` with optional trusted `ADMIN_UI_DEV_OPEN` for localhost-style sessions.
- `VITE_AUDIT_API_MODE` / `AUDIT_API_MODE` selects `mock` (default dev) vs `real` placeholder (`GET /admin/audit-events` once the gateway exposes it).
- See `docs/admin-audit-ui.md` for rollout guidance (service JWT never ships to browsers).

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
