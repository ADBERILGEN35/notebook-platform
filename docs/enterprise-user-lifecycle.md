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
- Faz 97 tombstone/re-add kurallari:
  - `deprovision_reason` ve `last_scim_external_id` saklanir.
  - Ayni `externalId` ile `active=true` re-add mevcut SCIM kullanicisini reactivate eder ve `reactivated_at` set eder.
  - Ayni email fakat farkli `externalId` otomatik linklenmez; conflict/manual review sinyali olarak ele alinir.
  - Delta response'ta eksik kullanici deprovision sebebi degildir.

## Admin Group Mapping

- SCIM grup kayitlari `scim_groups`, uyelikler `scim_group_memberships` uzerinden tutulur (USER ve istege bagli GROUP ic uyeleri).
- `SCIM_ADMIN_GROUPS` ile eslesen gruplar **effective** uyelik (direkt + ic ice aktif gruplar) uzerinden login tokeninda `PLATFORM_ADMIN` claimine katkida bulunur; deprovision/inaktif kullanici veya inaktif gruplar katki vermez.
- SSO claim mapping ve legacy allowlist modeli birlikte calismaya devam eder.

## Operasyonel Not

- Access tokenlar stateless oldugu icin mevcut access token son kullanma suresine kadar teorik pencere vardir.
- Bu nedenle kisa access token TTL + revoke akisi birlikte onerilir.
