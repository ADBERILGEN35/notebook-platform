# Configuration Profiles

Supported profiles are `local`, `dev`, `test` and `prod`.

| Profile | Fallbacks | Required secrets | Actuator | Logging | OTEL default | Flyway | Ephemeral JWT |
|---|---|---|---|---|---|---|---|
| `local` | Local DB/Redis defaults, optional internal tokens | None for app boot, DB password needed when connecting | `health,info,metrics,prometheus` | JSON in compose | Disabled | Enabled | Allowed in identity-service |
| `dev` | Same as local; file/env secrets recommended | None unless testing protected internal routes | `health,info,metrics,prometheus` | JSON in compose | Disabled | Enabled | Allowed in identity-service |
| `test` | Testcontainers/dynamic props | Test provides DB credentials | Narrow to test context | Test default | Disabled | Enabled | Allowed |
| `prod` | No insecure fallback | JWT keys, DB password, internal primary tokens | `health,info,prometheus` by default | JSON, no debug | Deployment controlled | Enabled | Forbidden |

Production hardening:

- identity-service requires `JWT_ALLOW_EPHEMERAL_KEYS=false`.
- identity-service requires configured signing keys or legacy `JWT_PRIVATE_KEY` / `JWT_PRIVATE_KEY_PATH`.
- identity-service requires an active `kid`; duplicate `kid` values fail fast.
- api-gateway requires `JWT_JWKS_URI`, `JWT_PUBLIC_KEY` or `JWT_PUBLIC_KEY_PATH`; JWKS is preferred.
- identity/workspace/content require non-blank `DB_PASSWORD`.
- workspace/content can split `DB_RUNTIME_*` from `DB_MIGRATION_*`; production should use a non-owner runtime role and a separate migration owner role.
- workspace-service requires `INTERNAL_API_TOKEN_PRIMARY`.
- content-service requires `WORKSPACE_INTERNAL_API_TOKEN_PRIMARY`.
- legacy `INTERNAL_API_TOKEN` and `WORKSPACE_INTERNAL_API_TOKEN` are rejected in prod.
- `APP_RLS_ENABLED` and `APP_RLS_STRICT_WORKSPACE_HEADER` default to `false`; production rollout should enable them only after non-owner runtime role and client header readiness are verified.
- Actuator must remain network-restricted; public gateway routes must not expose actuator endpoints beyond an intentional health route.
