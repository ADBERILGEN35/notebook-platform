# SIEM Streaming Push (Faz 62)

Bu fazda near-real-time SIEM push foundation `identity-service` uzerinde outbox + worker ile eklendi.

## Scope

MVP sadece high-value security event tiplerini stream eder:

- `USER_LOGIN_FAILED`
- `REFRESH_TOKEN_REVOKED`
- `REFRESH_TOKENS_REVOKED_ALL`
- `MFA_*` security eventleri
- `SSO_LOGIN_SUCCESS`, `SSO_LOGIN_FAILED`
- `SCIM_USER_DEPROVISIONED`, `SCIM_AUTH_FAILED`
- `USER_REFRESH_TOKENS_REVOKED_BY_SCIM`

`USER_LOGIN_SUCCEEDED` varsayilan olarak kapali, config ile acilabilir.

## Outbox modeli

Tablo: `siem_event_outbox`

- `PENDING` -> due ve publish bekliyor
- `SENDING` -> worker lock aldi
- `SENT` -> basarili push
- `DEAD` -> non-retryable veya max-attempt asildi

Retry/backoff:

- exponential backoff
- `SIEM_MAX_ATTEMPTS`
- `SIEM_BACKOFF_BASE_SECONDS`
- `SIEM_BACKOFF_MAX_SECONDS`

## Providerlar

- `noop`
- `log`
- `generic-http` (NDJSON POST)

`generic-http` response policy:

- `2xx`: success
- `429/5xx`: retry
- diger `4xx`: dead-letter

## Gateway kapsam notu

`api-gateway` su an kalici DB/outbox kullanmadigi icin Faz 62’de gateway tarafi persistent SIEM streaming yerine log/operasyonel signal seviyesinde tutuldu. Merkezilestirilmis cross-service stream future faza birakildi.

## Operational visibility (Faz 63)

SIEM worker/endpoint/secret-configured booleans (no tokens) are summarized for platform admins in
`docs/enterprise-admin-console.md`.
