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

## Error-State Validation

Using Playwright route interception:

- `401` -> session invalidation / redirect behavior
- `403` -> permission denied messaging
- `404` -> not found messaging
- `429` -> rate-limit messaging
- `503` -> service unavailable messaging
- `412` / `428` -> optimistic concurrency conflict banner + reload latest flow

## CI Strategy

- Workflow added as manual (`workflow_dispatch`) to avoid blocking default CI.
- Backend dependency is explicit; run only when gateway/backend stack is available.

## Known Limits

- Full backend orchestration for E2E is not automatic yet.
- Data cleanup is best-effort; test artifacts are prefixed with `E2E`.
- BlockNote DOM can be selector-sensitive; helper-level wrappers are used to reduce flaky behavior.
- Autosave timing is debounce-based; tests should wait on save status indicator rather than fixed sleeps.

