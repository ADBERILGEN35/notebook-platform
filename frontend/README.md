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

## Security Note

Tokens are stored in localStorage for MVP speed. Production should move to HttpOnly secure cookie
storage to reduce XSS token theft risk.

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
