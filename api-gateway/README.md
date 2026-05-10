# api-gateway

Spring Cloud Gateway tabanli API gateway. Faz 2 kapsaminda authentication validation, routing, rate limiting, request id ve identity header propagation sorumludur.

## Calistirma

Bagimliliklar:

```bash
docker compose -f docker-compose.dev.yml up -d
```

Gateway'i lokalde calistirma:

```bash
export JWT_PUBLIC_KEY_PATH=/absolute/path/to/public.pem
export JWT_JWKS_URI=http://localhost:8081/.well-known/jwks.json
export IDENTITY_SERVICE_URL=http://localhost:8081
export WORKSPACE_SERVICE_URL=http://localhost:8082
export CONTENT_SERVICE_URL=http://localhost:8083
export REDIS_HOST=localhost
export REDIS_PORT=6379
./gradlew :api-gateway:bootRun
```

Full compose:

```bash
JWT_PUBLIC_KEY_PATH=/run/secrets/jwt-public.pem docker compose up --build
```

Compose ile calistirirken tercih edilen yontem `JWT_JWKS_URI=http://identity-service:8081/.well-known/jwks.json` kullanmaktir. `JWT_PUBLIC_KEY_PATH` veya `JWT_PUBLIC_KEY` statik public key fallback'i geriye uyumluluk icin korunur.

## Env Degiskenleri

- `SERVER_PORT`: default `8080`
- `JWT_JWKS_URI`: identity-service JWKS endpointi; production icin onerilen validation yontemi
- `JWT_PUBLIC_KEY_PATH`: RS256 public key PEM dosya yolu
- `JWT_PUBLIC_KEY`: RS256 public key PEM icerigi, path yoksa kullanilir
- `IDENTITY_SERVICE_URL`: default `http://identity-service:8081`
- `WORKSPACE_SERVICE_URL`: default `http://workspace-service:8082`
- `CONTENT_SERVICE_URL`: default `http://content-service:8083`
- `REDIS_HOST`, `REDIS_PORT`: Redis rate limiting icin
- `REDIS_PASSWORD`: Redis auth etkinse kullanilir; dev'de bos olabilir, production'da Redis auth onerilir
- `CORS_ALLOWED_ORIGINS`: virgulle ayrilmis origin listesi, default `http://localhost:3000,http://localhost:5173`
- `CORS_ALLOW_CREDENTIALS`: cookie auth mode icin `true`
- `AUTH_TOKEN_TRANSPORT`: `bearer|cookie|dual`
- `AUTH_ACCESS_COOKIE_NAME`: cookie transportta access token cookie adi
- `AUTH_CSRF_COOKIE_NAME`, `AUTH_CSRF_HEADER_NAME`: CSRF double-submit alanlari
- `AUTH_RATE_LIMIT_REPLENISH_RATE`, `AUTH_RATE_LIMIT_BURST_CAPACITY`, `AUTH_RATE_LIMIT_REQUESTED_TOKENS`
- `PROTECTED_RATE_LIMIT_REPLENISH_RATE`, `PROTECTED_RATE_LIMIT_BURST_CAPACITY`, `PROTECTED_RATE_LIMIT_REQUESTED_TOKENS`
- `GATEWAY_ADMIN_ENABLED`, `GATEWAY_ADMIN_AUDIT_ENABLED`, `GATEWAY_ADMIN_ENTERPRISE_ENABLED` (Faz 63: `GET /admin/enterprise/status`)
- `GATEWAY_ADMIN_WRITE_ENABLED`, `GATEWAY_ADMIN_CHANGE_REQUESTS_INTERNAL_PATH`, `GATEWAY_ADMIN_WRITE_RATE_LIMIT_*` (Faz 77: `/admin/enterprise/change-requests`)
- `NOTIFICATION_SERVICE_URL` (enterprise status aggregation + notification analytics proxy)
- `GATEWAY_ADMIN_ENTERPRISE_NOTIFICATION_ANALYTICS_PATH` (Faz 81: upstream path for `GET /admin/notifications/analytics/summary`)
- `GATEWAY_ADMIN_ALLOWED_USER_IDS`, `GATEWAY_ADMIN_ALLOWED_EMAILS`
- `ADMIN_AUDIT_RATE_LIMIT_REPLENISH_RATE`, `ADMIN_AUDIT_RATE_LIMIT_BURST_CAPACITY`, `ADMIN_AUDIT_RATE_LIMIT_REQUESTED_TOKENS`
- `GATEWAY_ADMIN_AUDIT_SERVICE_JWT_*` (gateway signer for `/admin/audit-events` proxy calls)

## Routes

Public auth routes:

- `POST /auth/signup` -> identity-service
- `POST /auth/login` -> identity-service
- `POST /auth/refresh` -> identity-service
- `GET /auth/sso/**` -> identity-service (public redirect flow)

Protected routes:

- `/auth/**` -> identity-service
- `/workspaces/**`, `/notebooks/**`, `/tags/**`, `/invitations/**` -> workspace-service
- `/notes/**`, `/comments/**` -> content-service
- `/admin/audit-events` -> gateway controller (platform-admin auth + internal audit proxy fan-out)
- `/admin/enterprise/status` -> gateway controller (platform-admin auth + service JWT fan-out to identity/notification internal status)
- `/admin/enterprise/change-requests` (+ `/validate`, `/{id}/cancel`, `/{id}/approve`, `/{id}/reject`) -> gateway controller (platform-admin + MFA for writes, service JWT to identity internal change-requests API; Faz 77–78)
- `/admin/notifications/analytics/summary` -> gateway controller (enterprise admin + `admin:notifications:analytics:read`; Faz 81 service JWT to notification-service)
- `/admin/notifications/dead-letter` (+ `POST .../requeue/dry-run`, `POST .../requeue`) -> gateway controller (Faz 82; read vs requeue permissions + admin-write MFA for requeue)
- `/admin/notifications/retention/plan`, `POST /admin/notifications/retention/run` -> gateway controller (Faz 83; read vs run permissions + admin-write MFA for destructive)
- `/admin/notifications/legal-holds` (GET, POST, POST `.../{id}/release`) -> gateway controller (Faz 84; legal-hold read/write + admin-write MFA for mutations)

Public actuator:

- `GET /actuator/health`
- `GET /actuator/health/liveness`
- `GET /actuator/health/readiness`

## JWT ve Header Propagation

Protected endpointlerde bearer mode `Authorization: Bearer <accessToken>` zorunludur. Cookie mode aktifse gateway access tokeni cookie'den okuyabilir. Gateway `JWT_JWKS_URI` varsa JWT header `kid` degerine gore JWKS'ten dogru public key'i secer. JWKS URI yoksa statik public key fallback ile validate eder.

Production profilinde `JWT_JWKS_URI`, `JWT_PUBLIC_KEY` veya `JWT_PUBLIC_KEY_PATH` zorunludur; JWKS onerilen yontemdir. Unknown `kid`, invalid signature ve malformed token `401 INVALID_ACCESS_TOKEN`; expired token `401 EXPIRED_ACCESS_TOKEN`; refresh token `401 INVALID_TOKEN_TYPE` doner.

Client'tan gelen `X-User-Id`, `X-User-Email`, `X-User-Roles`, `X-Workspace-Role` headerlari downstream'e gecmeden silinir. Protected requestlerde gateway JWT'den uretir:

- `X-User-Id = sub`
- `X-User-Email = email`

`X-Workspace-Id` client header olarak gelebilir. Varsa UUID formatinda validate edilir ve downstream'e aktarilir; yoksa eklenmez. Workspace membership veya role kontrolu gateway'de yapilmaz.

## Rate Limiting

Redis backed token bucket kullanilir.

- Public auth endpointleri: key client IP
- Protected endpointler: key JWT `sub`
- `/admin/audit-events` and `/admin/enterprise/status`: key JWT `sub` with dedicated admin-audit bucket

Limitler `application.yml` ve env degiskenleri ile ayarlanabilir. Limit asilinca body formatli `429 RATE_LIMIT_EXCEEDED` doner.

## Error Format

Gateway hata yanitlari identity-service formatini korur:

```json
{
  "timestamp": "2026-04-29T12:00:00Z",
  "status": 401,
  "errorCode": "INVALID_ACCESS_TOKEN",
  "message": "Invalid access token",
  "path": "/workspaces/test",
  "requestId": "11111111-1111-1111-1111-111111111111"
}
```

## Observability

- Console log format JSON'dur.
- `X-Request-Id` yoksa gateway UUID uretir; invalid client value kabul edilmez, yeni UUID uretilir.
- `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness`, `/actuator/metrics`, `/actuator/prometheus` hazirdir.
- OTLP HTTP tracing `OTEL_ENABLED=true` ve `OTEL_EXPORTER_OTLP_ENDPOINT` ile acilir.

## Curl Ornekleri

Login gateway uzerinden:

```bash
curl -s http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"user1@example.com","password":"Password1234"}'
```

Protected endpoint:

```bash
curl -s http://localhost:8080/workspaces \
  -H "Authorization: Bearer <accessToken>"
```

Workspace header ile:

```bash
curl -s http://localhost:8080/notebooks \
  -H "Authorization: Bearer <accessToken>" \
  -H "X-Workspace-Id: 11111111-1111-1111-1111-111111111111"
```

Invalid token:

```bash
curl -i http://localhost:8080/workspaces \
  -H "Authorization: Bearer invalid"
```

Rate limit denemesi:

```bash
for i in $(seq 1 20); do
  curl -i -s http://localhost:8080/auth/login \
    -H "Content-Type: application/json" \
    -d '{"email":"user1@example.com","password":"wrong"}' | head -n 1
done
```

## Test

```bash
./gradlew :api-gateway:test
./gradlew :api-gateway:check
```

## Production Hardening

- `SPRING_PROFILES_ACTIVE=prod` ile `JWT_PUBLIC_KEY_PATH` veya `JWT_PUBLIC_KEY` zorunlu olur.
- Cookie mode icin explicit `CORS_ALLOWED_ORIGINS` ve `CORS_ALLOW_CREDENTIALS=true` birlikte
  kullanilmalidir.
- Actuator exposure env ile daraltilmalidir; public internet'e acik birakilmamalidir.
- Redis password ileride `REDIS_PASSWORD` ile verilebilir; local dev default'u bos kalir.

## Admin Audit Export (Faz 48)

- Endpoint: `GET /admin/audit-events/export`
- Required query: `source`, `format`, `createdFrom`, `createdTo`
- Formats: `csv`, `jsonl`
- Export guardrails:
  - range-day limit
  - max-record limit
  - dedicated export rate-limit bucket
- Service JWT remains server-side (gateway -> internal audit endpoints).

## Admin MFA preparation (Faz 51)

- Config: `ADMIN_REQUIRE_MFA=false` (default).
- When enabled, admin authorization also requires `mfa_verified=true` or `amr` containing
  `webauthn`/`recovery_code`.

## Admin MFA rollout (Faz 52+53)

- `GATEWAY_ADMIN_MFA_MODE=off|observe|warn|enforce`
- `GATEWAY_ADMIN_REQUIRE_MFA` (backward-compatible boolean)
- `GATEWAY_ADMIN_MFA_ACCEPTED_METHODS=webauthn,recovery_code`
- On admin endpoints, failing MFA policy returns `403 ADMIN_MFA_REQUIRED`.

## Audit export machine identity (Faz 54)

- `GET /admin/audit-events/export` supports machine JWT (`token_type=machine`) with strict policy:
  issuer allowlist, audience, required scope and max TTL.
- Machine tokens are blocked on non-export endpoints.
- Config:
  - `GATEWAY_AUDIT_EXPORT_MACHINE_AUTH_ENABLED`
  - `GATEWAY_AUDIT_EXPORT_MACHINE_AUTH_ALLOWED_ISSUERS`
  - `GATEWAY_AUDIT_EXPORT_MACHINE_AUTH_AUDIENCE`
  - `GATEWAY_AUDIT_EXPORT_MACHINE_AUTH_REQUIRED_SCOPE`
  - `GATEWAY_AUDIT_EXPORT_MACHINE_AUTH_MAX_TTL_SECONDS`

## Enterprise SSO admin identity (Faz 60)

- Gateway admin authorization now prefers `platform_roles` claim for `PLATFORM_ADMIN`.
- Legacy `roles` claim and allowlist (`GATEWAY_ADMIN_ALLOWED_*`) remain supported.
- SSO group mapping stays in identity-service; gateway is kept IdP-agnostic.

## Fine-grained admin RBAC (Faz 79)

- `GATEWAY_ADMIN_RBAC_ENFORCE` (default `false`): when `true`, admin routes require JWT `platform_permissions` (or `PLATFORM_ADMIN`), not email allowlist alone.
- See `docs/admin-rbac.md` and `docs/admin-permission-matrix.md`.

## SCIM routing (Faz 61)

- `/scim/v2/**` (Users, Groups, **Bulk**, ServiceProviderConfig, etc.) is routed to `identity-service`. CSRF is not applied to SCIM (bearer-only provisioning). SCIM rate limit bucket applies to the whole path prefix.
- Gateway JWT auth is bypassed for SCIM path; SCIM bearer validation is enforced by identity-service.
- SCIM path has dedicated rate limit bucket (`SCIM_RATE_LIMIT_*`).

## SIEM integration scope (Faz 62)

- Faz 62 persistent SIEM outbox implementation `identity-service` tarafinda yapildi.
- `api-gateway` kritik security/admin olaylari mevcut structured logging ile korunur.
- Gateway icin durable outbox veya central event bus bu faz kapsaminda degildir.
