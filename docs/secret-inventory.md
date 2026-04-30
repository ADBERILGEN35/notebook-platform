# Secret Inventory

Faz 9 standardizes runtime secrets around environment variables, file-based secret paths and the provider-agnostic `SecretProvider` contract in `common-security`.

| Config | Services | Secret? | Prod required? | Rotation? | Local/dev default? | Never in response/log? |
|---|---|---:|---:|---:|---:|---:|
| `JWT_PRIVATE_KEY` | identity-service | Yes | Yes, unless `JWT_PRIVATE_KEY_PATH` is set | Yes | No; dev may use ephemeral key | Yes |
| `JWT_PRIVATE_KEY_PATH` | identity-service | No, points to secret | Yes, unless `JWT_PRIVATE_KEY` is set | Yes, by replacing file/secret | Empty allowed in dev/test | Do not log contents |
| `JWT_PUBLIC_KEY` | identity-service, api-gateway | Public material, sensitive config | Gateway prod yes unless path is set | Yes | Empty allowed in identity dev/test | Do not echo full PEM |
| `JWT_PUBLIC_KEY_PATH` | identity-service, api-gateway | No, points to config/secret | Gateway prod yes unless inline key is set | Yes | Empty allowed in identity dev/test | Do not log contents |
| `JWT_KEYS_ACTIVE_KID` | identity-service | No | Yes | Yes | `primary` default | No |
| `JWT_KEYS_SIGNING_KEYS_*_KID` | identity-service | No | Yes for configured keys | Yes | Empty allowed | No |
| `JWT_KEYS_SIGNING_KEYS_*_PRIVATE_KEY_PATH` | identity-service | No, points to secret | Preferred prod key config | Yes | Empty allowed | Do not log contents |
| `JWT_KEYS_SIGNING_KEYS_*_PUBLIC_KEY_PATH` | identity-service | No, points to public key | Preferred prod key config | Yes | Empty allowed | Do not log contents |
| `JWT_JWKS_URI` | api-gateway | No | Preferred prod validation config | No | Compose default | No |
| `DB_PASSWORD` | identity-service, workspace-service, content-service | Yes | Yes | Yes | Compose local example only | Yes |
| `DB_RUNTIME_PASSWORD` | workspace-service, content-service | Yes | Required when runtime DB role is separated | Yes | Empty allowed; falls back to `DB_PASSWORD` | Yes |
| `DB_MIGRATION_PASSWORD` | workspace-service, content-service | Yes | Required when Flyway migration role is separated | Yes | Empty allowed; falls back to `DB_PASSWORD` | Yes |
| `DB_RUNTIME_USER` | workspace-service, content-service | No, but sensitive config | Recommended prod runtime role | When rotating role | Empty allowed; falls back to `DB_USER` | Avoid logging with URL/password |
| `DB_MIGRATION_USER` | workspace-service, content-service | No, but sensitive config | Recommended prod migration role | When rotating role | Empty allowed; falls back to `DB_USER` | Avoid logging with URL/password |
| `REDIS_PASSWORD` | api-gateway | Yes when Redis auth is enabled | Recommended for prod | Yes | Empty allowed in dev | Yes |
| `INTERNAL_API_TOKEN_PRIMARY` | workspace-service | Yes | Yes | Yes | Empty allowed in local/dev | Yes |
| `INTERNAL_API_TOKEN_SECONDARY` | workspace-service | Yes | No | Yes | Empty allowed | Yes |
| `INTERNAL_API_TOKEN` | workspace-service | Yes, legacy | No; rejected in prod | Migrate off | Dev fallback only | Yes |
| `WORKSPACE_INTERNAL_API_TOKEN_PRIMARY` | content-service | Yes | Yes | Yes | Empty allowed in local/dev | Yes |
| `WORKSPACE_INTERNAL_API_TOKEN_SECONDARY` | content-service | Yes | No | Yes | Empty allowed | Yes |
| `WORKSPACE_INTERNAL_API_TOKEN` | content-service | Yes, legacy | No; rejected in prod | Migrate off | Dev fallback only | Yes |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | all services | Usually no | No | No | Local collector default | Avoid credentials in URL |
| `OTEL_EXPORTER_OTLP_HEADERS` | all services / deployment | May contain token | If exporter requires auth | Yes | Empty allowed | Yes |
| `CORS_ALLOWED_ORIGINS` | api-gateway | No | Yes, explicit prod value | No | Local origins allowed | No |
| `ARGON2_*` | identity-service | No, security config | Defaults acceptable after review | No | Yes | No |
| `APP_RLS_ENABLED` | workspace-service, content-service | No, security config | Deployment decision | No | `false` default | No |
| `APP_RLS_STRICT_WORKSPACE_HEADER` | workspace-service, content-service | No, security config | Deployment decision | No | `false` default | No |

Rules:

- `application.yml` must not contain real secret values.
- Production uses primary internal token variables only; legacy token variables are development fallback and are rejected by prod validators.
- Secret values are represented with `SecretValue` where code needs to carry raw material; `toString()` and `masked()` never reveal the value.
- File-based secret paths are compatible with `/run/secrets/jwt_private_key_1`, `/run/secrets/jwt_public_key_1`, `/run/secrets/jwt_private_key_2`, `/run/secrets/jwt_public_key_2` and `/run/secrets/internal_api_token`.
