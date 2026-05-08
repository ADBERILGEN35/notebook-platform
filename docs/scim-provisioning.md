# SCIM Provisioning (Faz 61)

Bu fazda `identity-service` altinda SCIM 2.0 foundation eklendi.

## Endpointler

- `GET /scim/v2/ServiceProviderConfig`
- `GET /scim/v2/Schemas`
- `GET /scim/v2/ResourceTypes`
- `GET|POST|PUT|PATCH|DELETE /scim/v2/Users`
- `GET|POST|PUT|DELETE /scim/v2/Groups` (minimal foundation)

## Guvenlik Modeli

- SCIM sadece `Authorization: Bearer <token>` ile calisir.
- `SCIM_ENABLED=false` ise endpointler disabled davranir.
- Bearer token loglanmaz veya response'da donulmez.
- Desteklenen config:
  - `SCIM_BEARER_TOKEN` veya
  - `SCIM_BEARER_TOKEN_HASH` (onerilen)

## User Lifecycle

- `POST /Users`: SCIM source user olusturur.
- `PUT/PATCH /Users/{id}`: temel alanlar + `active` gunceller.
- `DELETE /Users/{id}`: hard delete degil, deprovision (`active=false`) uygular.
- Deprovision sirasinda aktif refresh tokenlar revoke edilir.

## Notlar ve Limitler

- Bulk operasyonlar bu fazda yok.
- SCIM PATCH full-spec path coverage yok; sadece gerekli yollar desteklenir.
- Group nesting/fan-out bu faz kapsaminda degil.

## Operational visibility (Faz 63)

SCIM enabled/token/admin-group flags (never the bearer token) appear in `docs/enterprise-admin-console.md`.
