# Frontend E2E Tests (Playwright)

## Prerequisites

- backend gateway running on `http://localhost:8080` (full suite)
- frontend app (Playwright webServer starts Vite automatically)
- Playwright browsers installed (`npx playwright install`)

Note: `admin-audit.spec.ts` stubs signup/workspace APIs and replaces `runtime-config.js` so the admin UI gates open without a live gateway; full-stack runs still assume the gateway for other specs.

## Environment

Copy `.env.e2e.example` and export values in your shell or CI.

Important variables:

- `E2E_BASE_URL` (default `http://localhost:5173`)
- `E2E_API_BASE_URL` (default `http://localhost:8080`)
- `VITE_API_BASE_URL` (frontend runtime API base)

## Run

```bash
npm run test:e2e
npm run test:e2e:headed
npm run test:e2e:ui
npm run test:e2e:debug
```

## Coverage

- `auth.spec.ts`: signup/login/logout/revoke-all and session checks
- `workspace-note.spec.ts`: workspace->notebook->note core journey
- `search.spec.ts`: note indexing + search with eventual consistency retry
- `comments-versions.spec.ts`: comment and version smoke
- `settings-security.spec.ts`: security page + 401/403/404/429/503 UI handling via interception
- `admin-audit.spec.ts`: audit explorer (mock mode + metadata masking + pagination) via Playwright route stubs

## Test Data Strategy

- Each run generates unique names and email suffixes using timestamp+random.
- Prefix pattern: `E2E Workspace`, `E2E Notebook`, `E2E Note`, `e2e+{suffix}@example.com`.
- Cleanup is best-effort. Because delete/archive endpoints are partial in MVP, E2E artifacts can
  remain in DB and are identified by the `E2E` prefix.

## Flaky Risk Notes

- Search is eventually consistent; tests use polling up to 30s.
- If backend startup or indexing is slow, increase Playwright timeout and polling intervals.

