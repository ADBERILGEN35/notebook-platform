# Internal Service Auth

Faz 13, static internal token modelini kirmadan service-to-service JWT altyapisi ekler. Ilk somut
akis `content-service -> workspace-service` internal permission cagrilaridir.

## Modes

`INTERNAL_AUTH_MODE` uc deger destekler:

| Mode | Davranis | Kullanim |
| --- | --- | --- |
| `static-token` | Sadece `X-Internal-Token` kabul edilir/gonderilir. | Local fallback veya rollback |
| `service-jwt` | Sadece `X-Service-Authorization: Bearer <jwt>` kabul edilir/gonderilir. | Production hedefi |
| `dual` | Service JWT varsa dogrulanir; yoksa static token fallback denenir. Client tarafinda service JWT tercih edilir. | Gecis modu |

Local/dev default `dual` kalir. Production icin onerilen mod `service-jwt`'dir.

## Service JWT Contract

Header:

- `alg=RS256`
- `kid=<active key id>`
- `typ=JWT`

Claims:

- `iss`: issuing service, ornek `content-service`
- `sub`: service account identity, ornek `service:content-service`
- `aud`: target service, ornek `workspace-service`
- `scope`: space-separated service scopes
- `iat`
- `exp`
- `jti`
- `service_name`
- `token_type=service`

Default TTL `INTERNAL_SERVICE_JWT_TTL_SECONDS=60` saniyedir. Kisa TTL nedeniyle content-service her
internal request icin yeni token uretir; cache bilincli olarak eklenmedi.

## Scopes

workspace-service internal endpointleri endpoint bazli scope ister:

| Endpoint | Required scope |
| --- | --- |
| `GET /internal/notebooks/{notebookId}/permissions` | `internal:workspace:permission:read` |
| `GET /internal/workspaces/{workspaceId}/tags/{tagId}/exists` | `internal:workspace:tag:read` |

## Key Model

Faz 13 provider-agnostic RSA key modelini kullanir:

- content-service service JWT'yi kendi private key'i ile imzalar.
- workspace-service content-service public key'i ile dogrular.
- Shared signing key kullanilmadi.
- JWKS endpoint eklenmedi; Faz 13 kapsaminda public key path fallback yeterli tutuldu.

content-service config:

- `INTERNAL_SERVICE_JWT_ACTIVE_KID`
- `INTERNAL_SERVICE_JWT_PRIVATE_KEY_PATH`
- `INTERNAL_SERVICE_JWT_PUBLIC_KEY_PATH`
- `INTERNAL_SERVICE_JWT_ISSUER=content-service`
- `INTERNAL_SERVICE_JWT_TTL_SECONDS=60`

workspace-service trusted service config:

- `TRUSTED_SERVICE_CONTENT_SERVICE_KID`
- `TRUSTED_SERVICE_CONTENT_SERVICE_PUBLIC_KEY_PATH`
- `TRUSTED_SERVICE_CONTENT_SERVICE_ISSUER=content-service`
- `TRUSTED_SERVICE_CONTENT_SERVICE_AUDIENCE=workspace-service`

## Error Codes

| Code | HTTP | Meaning |
| --- | ---: | --- |
| `INTERNAL_AUTH_REQUIRED` | 401 | Required internal auth header is missing |
| `INVALID_INTERNAL_TOKEN` | 401 | Static token is wrong |
| `INVALID_SERVICE_JWT` | 401 | JWT malformed, bad signature, bad kid or bad token type |
| `EXPIRED_SERVICE_JWT` | 401 | JWT expired |
| `INVALID_SERVICE_AUDIENCE` | 401 | `aud` does not match workspace-service |
| `INVALID_SERVICE_ISSUER` | 401 | `iss` is not trusted |
| `INSUFFICIENT_SERVICE_SCOPE` | 403 | JWT lacks endpoint scope |

Tokens and key material are never echoed in error responses.

## Migration Plan

1. Deploy workspace-service and content-service in `dual` mode.
2. Configure content-service signing key and workspace-service trusted public key.
3. Confirm workspace-service accepts service JWT and content-service sends `X-Service-Authorization`.
4. Switch production `INTERNAL_AUTH_MODE=service-jwt`.
5. Remove static token envs after rollback window.

Rollback:

- Set `INTERNAL_AUTH_MODE=dual` and keep static tokens configured.
- If key distribution is broken, set `INTERNAL_AUTH_MODE=static-token` until service JWT trust config is fixed.

Remaining gap: mTLS is not implemented in Faz 13. Service JWT authenticates application-layer caller
identity, but transport identity and certificate lifecycle remain future deployment hardening work.

## Kubernetes Notes

The Helm chart mounts service JWT keys from Kubernetes Secret:

- content-service signing key: `/etc/notebook/secrets/service-jwt/content-private.pem`
- workspace-service trusted public key: `/etc/notebook/secrets/service-jwt/content-public.pem`

`values-prod.example.yaml` sets `INTERNAL_AUTH_MODE=service-jwt`. Static tokens should only be kept
for a controlled rollback window.
