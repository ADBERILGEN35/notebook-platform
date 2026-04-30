# Resilience

## Policies

Service-to-service permission and lookup calls use fail-closed behavior.

- `content-service -> workspace-service` permission/tag checks:
  - Circuit breaker: `workspacePermission`
  - Retry: idempotent GET calls only
  - Timeout: configured on the HTTP client request factory
  - Failure response: `503 WORKSPACE_SERVICE_UNAVAILABLE`
  - Internal authentication: sends `X-Internal-Token` when `WORKSPACE_INTERNAL_API_TOKEN` is configured
  - Fail-closed: permission lookup failures never grant access

- `workspace-service -> identity-service` invitation email lookup:
  - Circuit breaker: `identityLookup`
  - Retry: idempotent GET call only
  - Timeout: configured on the HTTP client request factory
  - Failure behavior: graceful fallback; invitation creation continues because lookup is optional in Faz 3/5

No automatic retry is applied to mutating POST/PUT/PATCH flows.

## Configuration

Content-service:

- `WORKSPACE_CLIENT_TIMEOUT_MS`
- `WORKSPACE_CLIENT_CB_FAILURE_THRESHOLD`
- `WORKSPACE_CLIENT_CB_OPEN_STATE_MS`
- `WORKSPACE_CLIENT_RETRY_MAX_ATTEMPTS`
- `WORKSPACE_INTERNAL_API_TOKEN`

Workspace-service:

- `IDENTITY_CLIENT_TIMEOUT_MS`
- `IDENTITY_CLIENT_CB_FAILURE_THRESHOLD`
- `IDENTITY_CLIENT_CB_OPEN_STATE_MS`
- `IDENTITY_CLIENT_RETRY_MAX_ATTEMPTS`
- `INTERNAL_API_TOKEN`

`INTERNAL_API_TOKEN` protects workspace-service `/internal/**` endpoints when configured. It is optional for local development, but production should set it and keep it out of source control.
Faz 8 adds rotation readiness with `INTERNAL_API_TOKEN_PRIMARY`, `INTERNAL_API_TOKEN_SECONDARY` and `WORKSPACE_INTERNAL_API_TOKEN_PRIMARY`.
