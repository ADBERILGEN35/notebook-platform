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
- `AUTH_RATE_LIMIT_REPLENISH_RATE`, `AUTH_RATE_LIMIT_BURST_CAPACITY`, `AUTH_RATE_LIMIT_REQUESTED_TOKENS`
- `PROTECTED_RATE_LIMIT_REPLENISH_RATE`, `PROTECTED_RATE_LIMIT_BURST_CAPACITY`, `PROTECTED_RATE_LIMIT_REQUESTED_TOKENS`

## Routes

Public auth routes:

- `POST /auth/signup` -> identity-service
- `POST /auth/login` -> identity-service
- `POST /auth/refresh` -> identity-service

Protected routes:

- `/auth/**` -> identity-service
- `/workspaces/**`, `/notebooks/**`, `/tags/**`, `/invitations/**` -> workspace-service
- `/notes/**`, `/comments/**` -> content-service

Public actuator:

- `GET /actuator/health`
- `GET /actuator/health/liveness`
- `GET /actuator/health/readiness`

## JWT ve Header Propagation

Protected endpointlerde `Authorization: Bearer <accessToken>` zorunludur. Gateway `JWT_JWKS_URI` varsa JWT header `kid` degerine gore JWKS'ten dogru public key'i secer. JWKS URI yoksa statik public key fallback ile validate eder. `token_type=access` disindaki tokenlari reddeder.

Production profilinde `JWT_JWKS_URI`, `JWT_PUBLIC_KEY` veya `JWT_PUBLIC_KEY_PATH` zorunludur; JWKS onerilen yontemdir. Unknown `kid`, invalid signature ve malformed token `401 INVALID_ACCESS_TOKEN`; expired token `401 EXPIRED_ACCESS_TOKEN`; refresh token `401 INVALID_TOKEN_TYPE` doner.

Client'tan gelen `X-User-Id`, `X-User-Email`, `X-User-Roles`, `X-Workspace-Role` headerlari downstream'e gecmeden silinir. Protected requestlerde gateway JWT'den uretir:

- `X-User-Id = sub`
- `X-User-Email = email`

`X-Workspace-Id` client header olarak gelebilir. Varsa UUID formatinda validate edilir ve downstream'e aktarilir; yoksa eklenmez. Workspace membership veya role kontrolu gateway'de yapilmaz.

## Rate Limiting

Redis backed token bucket kullanilir.

- Public auth endpointleri: key client IP
- Protected endpointler: key JWT `sub`

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
- Actuator exposure env ile daraltilmalidir; public internet'e acik birakilmamalidir.
- Redis password ileride `REDIS_PASSWORD` ile verilebilir; local dev default'u bos kalir.
