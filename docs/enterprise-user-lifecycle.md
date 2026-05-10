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

- SCIM grup kayitlari `scim_groups`, uyelikler `scim_group_memberships` uzerinden tutulur (USER ve istege bagli GROUP ic uyeleri).
- `SCIM_ADMIN_GROUPS` ile eslesen gruplar **effective** uyelik (direkt + ic ice aktif gruplar) uzerinden login tokeninda `PLATFORM_ADMIN` claimine katkida bulunur; deprovision/inaktif kullanici veya inaktif gruplar katki vermez.
- SSO claim mapping ve legacy allowlist modeli birlikte calismaya devam eder.

## Operasyonel Not

- Access tokenlar stateless oldugu icin mevcut access token son kullanma suresine kadar teorik pencere vardir.
- Bu nedenle kisa access token TTL + revoke akisi birlikte onerilir.
