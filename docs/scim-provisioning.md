# SCIM Provisioning (Faz 61, Faz 76)

Bu dokuman SCIM 2.0 foundation (Faz 61) ve grup nesting + bulk MVP (Faz 76) davranisini ozetler.

## Endpointler

- `GET /scim/v2/ServiceProviderConfig`
- `GET /scim/v2/Schemas`
- `GET /scim/v2/ResourceTypes`
- `GET|POST|PUT|PATCH|DELETE /scim/v2/Users`
- `GET|POST|PUT|PATCH|DELETE /scim/v2/Groups`
- `POST /scim/v2/Bulk` (flag: `SCIM_BULK_ENABLED`, default kapali)

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

- Bulk: MVP, transactional rollback yok; ayrintilar `docs/scim-bulk-operations.md`.
- SCIM PATCH full-spec path coverage yok; sadece desteklenen yollar.
- Grup nesting: `docs/scim-group-nesting.md`. Derinlik ve dongu limitleri `SCIM_GROUP_NESTING_*` ile konfigüre edilir.

## Operational visibility (Faz 63)

SCIM enabled/token/admin-group flags (never the bearer token) appear in `docs/enterprise-admin-console.md`.
