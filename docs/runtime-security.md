# Runtime Security

## Internal Service Authentication

Faz 13 supports service-to-service JWT for internal calls while keeping static tokens as a
backward-compatible fallback:

- `INTERNAL_AUTH_MODE=static-token`: only `X-Internal-Token`.
- `INTERNAL_AUTH_MODE=service-jwt`: only `X-Service-Authorization: Bearer <jwt>`.
- `INTERNAL_AUTH_MODE=dual`: service JWT preferred, static token fallback.
- content-service signs short-lived RS256 service JWTs for workspace-service.
- workspace-service validates issuer, audience, `token_type=service`, `kid`, expiration and endpoint scope.

Details are in [`internal-service-auth.md`](internal-service-auth.md).
Kubernetes secret delivery options are in [`external-secrets.md`](external-secrets.md).

Earlier static token hardening remains available:

- workspace-service validates `X-Internal-Token` centrally for `/internal/**`.
- content-service sends `X-Internal-Token` from `WORKSPACE_INTERNAL_API_TOKEN_PRIMARY`.
- workspace-service requires `INTERNAL_API_TOKEN_PRIMARY` when `SPRING_PROFILES_ACTIVE=prod`.
- content-service requires `WORKSPACE_INTERNAL_API_TOKEN_PRIMARY` when `SPRING_PROFILES_ACTIVE=prod`.
- Tokens are never returned in responses and should not be logged.

Static tokens are acceptable for MVP hardening. mTLS or short-lived service JWTs remain the recommended production target.

## Token Rotation Readiness

Faz 8 adds primary/secondary static token support:

- workspace-service accepts `INTERNAL_API_TOKEN_PRIMARY` and optional `INTERNAL_API_TOKEN_SECONDARY`.
- legacy `INTERNAL_API_TOKEN` remains a local/dev primary-token fallback and is rejected in prod.
- content-service sends `WORKSPACE_INTERNAL_API_TOKEN_PRIMARY`, falling back to `WORKSPACE_INTERNAL_API_TOKEN`.
- token comparison is performed with constant-time byte comparison.

Rotation flow:

1. Set new token as `INTERNAL_API_TOKEN_SECONDARY` in workspace-service.
2. Deploy workspace-service.
3. Set content-service `WORKSPACE_INTERNAL_API_TOKEN_PRIMARY` to the new token.
4. Deploy content-service.
5. Promote new token to `INTERNAL_API_TOKEN_PRIMARY`.
6. Remove old secondary token after traffic confirms success.

Service JWT or mTLS is still the preferred long-term approach because static shared tokens do not identify individual callers.

Detailed rotation steps are maintained in [`secret-rotation-runbook.md`](secret-rotation-runbook.md).

## JWT JWKS/kid Validation

identity-service signs access and refresh tokens with `alg=RS256` and `kid=<activeKid>`.

- `GET /.well-known/jwks.json` returns public RSA keys only.
- api-gateway prefers `JWT_JWKS_URI` and validates access tokens by `kid`.
- Static `JWT_PUBLIC_KEY` / `JWT_PUBLIC_KEY_PATH` gateway validation remains as a fallback.
- identity-service validates refresh tokens by reading the token header `kid` and selecting the matching configured public key.
- Unknown `kid` is rejected.

Old JWT keys must remain configured until their access tokens and refresh tokens expire, unless an emergency rotation intentionally invalidates them.

## Runtime Tenant Enforcement

Application-level tenant enforcement remains the active control:

- Gateway validates `X-Workspace-Id` format.
- workspace-service checks membership for workspace-scoped operations.
- content-service asks workspace-service for notebook/tag permissions.
- content-service rejects conflicting `X-Workspace-Id` context.

Faz 11 adds runtime tenant binding for workspace-service and content-service:

- `TenantContext` stores the current workspace id in a servlet-thread `ThreadLocal`.
- `TenantCleanupFilter` clears the context after every request.
- `TenantDatabaseSession.applyWorkspace(workspaceId)` sets the ThreadLocal and, when `APP_RLS_ENABLED=true`, executes `SET LOCAL app.current_workspace_id = '<workspace-id>'` inside the active service transaction.
- The feature flag default is `false` in local/dev/prod templates. Integration tests run the tenant session with the flag enabled.

Faz 12 adds the deployment foundation for making this DB-level control real:

- Runtime datasource credentials can be separated from Flyway migration credentials through `DB_RUNTIME_*` and `DB_MIGRATION_*`.
- Non-owner runtime role setup templates live under `scripts/db/*-create-roles.sql`.
- `FORCE ROW LEVEL SECURITY` remains opt-in SQL, not a migration, until deployment-like tests pass.
- `APP_RLS_STRICT_WORKSPACE_HEADER=true` can require `X-Workspace-Id` for aggregate-id requests so lookup queries can run under a tenant context.

Important limitation: application-level authorization remains the primary always-on control. DB-level blocking is guaranteed only for flows tested with `APP_RLS_ENABLED=true`, a non-owner runtime DB role, and the relevant RLS/optional FORCE RLS policy enabled.

See [`database-roles-and-rls.md`](database-roles-and-rls.md) for role setup, FORCE RLS scripts and rollback.
See [`runtime-rls-rollout.md`](runtime-rls-rollout.md) for Stage 0-5 production rollout,
preflight checks, smoke tests and rollback order.

## Tenant Context Classification

| Endpoint | Service | Tenant source | Category | SET LOCAL ready? | Refactor needed? | Current enforcement | Target enforcement |
|---|---|---|---|---:|---:|---|---|
| `GET /workspaces` | workspace | user membership list | C | No | Yes for forced RLS | application membership | user-level policy or separate non-RLS lookup |
| `POST /workspaces` | workspace | newly generated workspace id | C/A after id generation | Yes | No | owner membership create | set tenant before scoped inserts |
| `/workspaces/{workspaceId}` | workspace | path `workspaceId` | A | Yes | No | membership/role checks | app check + SET LOCAL |
| `/workspaces/{workspaceId}/members` | workspace | path `workspaceId` | A | Yes | No | role checks | app check + SET LOCAL |
| `/workspaces/{workspaceId}/notebooks` | workspace | path `workspaceId` | A | Yes | No | membership/role checks | app check + SET LOCAL |
| `/notebooks/{notebookId}` | workspace | notebook lookup | B | Yes after lookup | final forced-RLS needs resolver query strategy | notebook/workspace permission | minimal lookup, then SET LOCAL |
| `/notebooks/{notebookId}/members` | workspace | notebook lookup | B | Yes after lookup | final forced-RLS needs resolver query strategy | notebook permission | minimal lookup, then SET LOCAL |
| `/workspaces/{workspaceId}/tags` | workspace | path `workspaceId` | A | Yes | No | membership/role checks | app check + SET LOCAL |
| `/tags/{tagId}` | workspace | tag lookup | B | Yes after lookup | final forced-RLS needs resolver query strategy | workspace role checks | minimal lookup, then SET LOCAL |
| `/notebooks/{notebookId}/tags/{tagId}` | workspace | notebook lookup + tag check | B | Yes after notebook lookup | final forced-RLS needs resolver query strategy | notebook manage + workspace match | minimal lookup, then SET LOCAL |
| `/workspaces/{workspaceId}/invitations` | workspace | path `workspaceId` | A | Yes | No | owner/admin checks | app check + SET LOCAL |
| `/invitations/accept` | workspace | invitation token lookup | B | Yes after lookup | final forced-RLS needs token resolver strategy | email/token validation | minimal lookup, then SET LOCAL |
| `/invitations/{invitationId}/revoke` | workspace | invitation lookup | B | Yes after lookup | final forced-RLS needs resolver query strategy | owner check | minimal lookup, then SET LOCAL |
| `/internal/notebooks/{notebookId}/permissions` | workspace | notebook lookup | B | Yes after lookup | final forced-RLS needs resolver query strategy | internal token + permission calc | minimal lookup, then SET LOCAL |
| `/internal/workspaces/{workspaceId}/tags/{tagId}/exists` | workspace | path `workspaceId` | A | Yes | No | internal token + scoped query | SET LOCAL |
| `POST /notebooks/{notebookId}/notes` | content | workspace permission response | B | Yes after permission call | No | workspace-service fail-closed permission | permission response, then SET LOCAL |
| `GET /notebooks/{notebookId}/notes` | content | workspace permission response | B | Yes after permission call | No | workspace-service fail-closed permission | permission response, then SET LOCAL |
| `/notes/{noteId}` | content | note lookup | B | Yes after lookup | final forced-RLS needs resolver query strategy | permission check + workspace header conflict check | minimal lookup, then SET LOCAL |
| `/notes/{noteId}/versions` | content | note lookup | B | Yes after lookup | final forced-RLS needs resolver query strategy | permission check | minimal lookup, then SET LOCAL |
| `/notes/{noteId}/links/*` | content | note lookup | B | Yes after lookup | final forced-RLS needs resolver query strategy | permission check | minimal lookup, then SET LOCAL |
| `/comments/{commentId}` | content | comment lookup then note lookup | B | Yes after note lookup | final forced-RLS needs resolver query strategy | ownership/manage permission | minimal lookup, then SET LOCAL |
| `/notes/{noteId}/comments` | content | note lookup | B | Yes after lookup | final forced-RLS needs resolver query strategy | permission check | minimal lookup, then SET LOCAL |
| `/notes/{noteId}/tags` | content | note lookup | B | Yes after lookup | final forced-RLS needs resolver query strategy | permission + tag check | minimal lookup, then SET LOCAL |
| `/notes/search?workspaceId=...` | content | query `workspaceId` | A | Yes | No | workspace header conflict + per-note permission filter | SET LOCAL + app filter |

Async boundary note: workspace-service and content-service use servlet request handling for these flows. The ThreadLocal tenant context is not propagated across async work; no tenant-scoped async repository access is introduced in this phase.

## Actuator Exposure

Dev can expose `health`, `metrics` and `prometheus`.

Production should expose:

- public: health through a protected edge path only if required
- internal: readiness/liveness/prometheus through private network or service discovery only

Do not route actuator endpoints through public gateway routes without authentication or network policy.

## Kubernetes Deployment Security

The Helm chart keeps identity/workspace/content as ClusterIP-only services and exposes only
api-gateway through optional Ingress. Pod/container security contexts run as non-root, drop
capabilities, disable privilege escalation and use a read-only root filesystem with `/tmp` mounted
as `emptyDir`.

NetworkPolicy is optional because CNI behavior is cluster-specific. The production example enables a
default policy that allows internal platform traffic, public ingress only to api-gateway and egress
to external DB/Redis/OTel targets.
