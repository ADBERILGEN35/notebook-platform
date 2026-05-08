# Enterprise User Lifecycle (Faz 61)

Bu dokuman Faz 60 SSO foundation uzerine gelen SCIM lifecycle davranisini ozetler.

## Provisioning

- Enterprise IdP, SCIM ile kullanici create/update/deactivate yapabilir.
- `users.source=SCIM` ve `users.scim_external_id` alanlari ile kaynak izlenir.

## Offboarding Guvencesi

- SCIM `active=false` veya `DELETE`:
  - kullanici `DISABLED` olur,
  - `deprovisioned_at` set edilir,
  - aktif refresh tokenlar revoke edilir.

## Admin Group Mapping

- SCIM grup kayitlari ve user membership kayitlari tutulur.
- `SCIM_ADMIN_GROUPS` ile eslesen gruplar login tokeninda `PLATFORM_ADMIN` claimine katkida bulunur.
- SSO claim mapping ve legacy allowlist modeli birlikte calismaya devam eder.

## Operasyonel Not

- Access tokenlar stateless oldugu icin mevcut access token son kullanma suresine kadar teorik pencere vardir.
- Bu nedenle kisa access token TTL + revoke akisi birlikte onerilir.
