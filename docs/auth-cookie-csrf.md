# Auth Cookie + CSRF Model (Faz 41)

## Transport Modes

- `bearer`: legacy Authorization header token flow
- `cookie`: httpOnly access/refresh cookies + CSRF double-submit
- `dual`: bearer ve cookie birlikte; kademeli rollout icin

## Cookies

Default names:

- access: `__Host-np_access`
- refresh: `__Host-np_refresh`
- csrf (readable): `NP-XSRF-TOKEN`

Important flags:

- production: `Secure=true` zorunlu
- `__Host-` prefix kullaniliyorsa `Domain` set edilmez ve `Path=/` olmalidir
- cross-site deployments (`app.*` -> `api.*`) icin pratikte `SameSite=None` + `Secure=true` gerekir

## CSRF Protection

Gateway double-submit validation:

- unsafe methods: `POST`, `PUT`, `PATCH`, `DELETE`
- safe methods: `GET`, `HEAD`, `OPTIONS`
- cookie/header eslesmesi:
  - cookie: `NP-XSRF-TOKEN`
  - header: `X-CSRF-Token`

Bypass paths:

- `/auth/login`
- `/auth/signup`
- `/webhooks/email/**`
- `/actuator/**`

Errors:

- `403 CSRF_TOKEN_REQUIRED`
- `403 CSRF_TOKEN_INVALID`

## CORS + Credentials

- cookie mode icin `CORS_ALLOW_CREDENTIALS=true`
- wildcard origin + credentials yasak
- `CORS_ALLOWED_ORIGINS` explicit host list olmalidir

## Rollout

1. backend `AUTH_TOKEN_TRANSPORT=dual`
2. frontend `AUTH_TRANSPORT=cookie`
3. staging login/refresh/logout/revoke-all + CSRF doğrulama
4. production `AUTH_TOKEN_TRANSPORT=cookie`
5. 401/403 oranlarini izle

## Rollback

- backend transport `dual` veya `bearer`
- frontend `AUTH_TRANSPORT=bearer`
- cookie clean-up logout ile devam eder

## MFA note (Faz 51)

- Login MFA-required durumunda access/refresh cookie hemen yazilmaz.
- Cookie issuance `POST /auth/mfa/webauthn/authentication/verify` veya
  `POST /auth/mfa/recovery-codes/verify` sonrasinda tamamlanir.
