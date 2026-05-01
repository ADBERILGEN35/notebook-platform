# identity-service

Kullanıcı kimlik doğrulama (signup/login/refresh) için MVP kimlik servisi.

## Gereksinimler

- PostgreSQL 16+ (Testcontainers’da otomatik; local için `docker compose`)
- Redis 7 (bu fazda identity servisinde zorunlu değil; root compose’da var)
- Java 25 LTS (Gradle toolchain ile otomatik)

## Local çalıştırma

### 1) Bağımlılıkları ayağa kaldır

```bash
docker compose -f docker-compose.dev.yml up -d
```

### 2) Identity servisi başlat

```bash
./gradlew :identity-service:bootRun
```

### 3) Sağlık ve endpointler

- Swagger UI: `http://localhost:8081/swagger-ui.html`
- Sağlık: `http://localhost:8081/actuator/health`

## RSA anahtarları (RS256)

JWT imzası için özel/umumi anahtarlar env veya dosya olarak sağlanır.

Varsayılan olarak `JWT_PRIVATE_KEY`, `JWT_PRIVATE_KEY_PATH`, `JWT_PUBLIC_KEY` ve `JWT_PUBLIC_KEY_PATH` verilmezse servis dev/test için ephemeral RSA anahtar çifti üretir. Production profilinde `JWT_ALLOW_EPHEMERAL_KEYS=false` ve `JWT_PRIVATE_KEY` veya `JWT_PRIVATE_KEY_PATH` zorunludur.

Faz 10 itibariyla tercih edilen production config çoklu key modelidir:

```bash
export JWT_KEYS_ACTIVE_KID=prod-key-1
export JWT_KEYS_SIGNING_KEYS_0_KID=prod-key-1
export JWT_KEYS_SIGNING_KEYS_0_PRIVATE_KEY_PATH=/run/secrets/jwt_private_key_1
export JWT_KEYS_SIGNING_KEYS_0_PUBLIC_KEY_PATH=/run/secrets/jwt_public_key_1
```

Token header'inda `alg=RS256` ve `kid=<activeKid>` bulunur. Public key seti `GET /.well-known/jwks.json` endpointinden RFC 7517 JWKS olarak doner; private key alanlari asla donmez.

### Anahtar üret (Örnek)

```bash
# private key (PKCS#8 üretmek için modern komut)
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:4096 -out private.pem

# public key
openssl rsa -pubout -in private.pem -out public.pem
```

Ardından dosya yollarını env’e verin.

## Örnek curl

### Signup

```bash
curl -s http://localhost:8081/auth/signup \
  -H "Content-Type: application/json" \
  -d '{
    "email":"user1@example.com",
    "password":"Password1234",
    "name":"Ada",
    "avatarUrl":null
  }'
```

### Login

```bash
curl -s http://localhost:8081/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email":"user1@example.com",
    "password":"Password1234"
  }'
```

### Refresh

```bash
curl -s http://localhost:8081/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken":"<refreshToken>"
  }'
```

### Logout

`/auth/logout` revokes one refresh token. It requires an access token and the refresh token body must
belong to the same user.

```bash
curl -i http://localhost:8081/auth/logout \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken":"<refreshToken>"
  }'
```

Success returns `204 No Content`. Repeating logout for the same valid, already revoked refresh token
is idempotent and also returns `204`.

### Revoke all refresh tokens

`/auth/revoke-all` revokes all active, unexpired refresh tokens for the access token subject.

```bash
curl -s http://localhost:8081/auth/revoke-all \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"reason":"ACCOUNT_SECURITY"}'
```

Response:

```json
{"revokedCount":2}
```

Existing access tokens are not immediately invalidated; they remain valid until their short TTL
expires. Future work may add session listing or access token blacklist/introspection.

## Testler

```bash
./gradlew :identity-service:test
./gradlew :identity-service:check
```

## Observability + Hardening

- Console logs JSON formatindadir.
- `X-Request-Id` response ve error body icinde doner; yoksa servis UUID uretir.
- Validation hatalari `fieldErrors` listesi doner.
- `/actuator/health`, `/actuator/metrics`, `/actuator/prometheus` hazirdir.
- OTLP HTTP tracing `OTEL_ENABLED=true` ile acilir.
- Production'da coklu key config veya legacy `JWT_PRIVATE_KEY`/`JWT_PRIVATE_KEY_PATH` mutlaka verilmelidir; ephemeral key uretimi sadece dev/test icindir.
- Production'da `DB_PASSWORD` bos olamaz.
- `SPRING_PROFILES_ACTIVE=prod` ile `jwt.allow-ephemeral-keys=false` olur; JWT key path/env yoksa servis fail-fast eder.
- Duplicate `kid` veya `activeKid` config'te yoksa servis fail-fast eder.
- Kritik auth aksiyonlari `identity_audit_events` tablosuna yazilir; token/password gibi hassas metadata alanlari maskelenir.
- Refresh token plaintext DB, response veya audit metadata icine yazilmaz. DB yalnizca hash ve session metadata saklar.

