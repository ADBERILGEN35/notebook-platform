# Frontend E2E Validation (Faz 36)

## Scope

Critical user journeys for the existing frontend MVP and gateway-backed backend contracts.

No new backend feature is introduced in this phase.

## Tooling

- Playwright + TypeScript
- Tests under `frontend/e2e`
- Environment-driven gateway/frontend base URLs

## Covered Journeys

- Auth: signup, login, invalid login, session persistence, logout, revoke-all presence
- Workspace/notebook/note: create and persist BlockNote content after reload
- Workspace/notebook/note also validates autosave status transitions in smoke path
- Search: create note and validate query result with eventual consistency polling
- Comments/versions: smoke checks for add/resolve/reopen and version list visibility
- Settings/security: security note + revoke-all + error-state interceptions
- Admin/audit mock: pagination + metadata masking (`e2e/admin-audit.spec.ts`)
- Admin/audit real-mode UI mappings (intercepted gateway responses): `403 ADMIN_ACCESS_DENIED`, `503 AUDIT_SOURCE_UNAVAILABLE`, success table render (`e2e/admin-audit-real-mode.spec.ts`)
- Admin RBAC UI: mocked `platform_permissions` / dev-open off — audit export hidden, approver without create, admin gate (`e2e/admin-rbac-ui.spec.ts`)
- Responsive shell smoke for mobile/tablet drawer controls (`e2e/responsive.spec.ts`)
- Conflict UX flow: review dialog + overwrite with latest ETag retry (`e2e/workspace-note.spec.ts`)

## Error-State Validation

Using Playwright route interception:

- `401` -> session invalidation / redirect behavior
- `403` -> permission denied messaging
- `404` -> not found messaging
- `429` -> rate-limit messaging
- `503` -> service unavailable messaging
- `412` / `428` -> optimistic concurrency conflict banner + conflict resolution dialog flow

## CI Strategy

- Workflow added as manual (`workflow_dispatch`) to avoid blocking default CI.
- Backend dependency is explicit; run only when gateway/backend stack is available.

## Known Limits

- When stubbing `GET /admin/enterprise/change-requests`, use a pathname predicate (see `e2e/helpers/gateway-stub-urls.ts`) — broad Playwright URL globs can match Vite source files such as `change-requests-api.ts` and break the dev server.
- Full backend orchestration for E2E is not automatic yet.
- Data cleanup is best-effort; test artifacts are prefixed with `E2E`.
- BlockNote DOM can be selector-sensitive; helper-level wrappers are used to reduce flaky behavior.
- Autosave timing is debounce-based; tests should wait on save status indicator rather than fixed sleeps.

## Deployed Environment Mode (Faz 40)

- Local Vite mode: existing developer flow.
- Docker frontend mode: run frontend image and point `FRONTEND_API_BASE_URL` to gateway URL.
- Deployed cluster mode: use frontend ingress host as Playwright base URL and gateway host as API target.
- `scripts/smoke-test-frontend.sh` can be used as pre-E2E quick gate for deployed frontend runtime.

