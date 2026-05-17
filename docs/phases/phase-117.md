# Faz 117: SCIM Delta Read-only Remote Fetch POC

## Hedef

Faz 116 diagnostic foundation üzerine, `SCIM_DELTA_REMOTE_FETCH_ENABLED=true` iken **yalnızca manual dry-run** endpoint'inin provider'a sınırlı **read-only GET** atabilmesi; yanıtın sanitize edilip yalnızca aggregate diagnostic üretilmesi.

## Scope

| Bileşen | Açıklama |
|---------|----------|
| `ScimDeltaProviderClient` | GET-only remote fetch abstraction |
| `ScimDeltaProviderRequest` / `ScimDeltaProviderFetchResult` | Request/aggregate result models |
| `ScimDeltaProviderRequestBuilder` | Okta lastModified, Entra `$top`, generic full-sync fallback URI |
| `ScimDeltaProviderResponseSanitizer` | Count + schema-shape + cursor presence only |
| `DefaultScimDeltaProviderClient` | JDK HttpClient; Faz 116 classifier integration |
| `ScimDeltaFetchDiagnosticsService` | Remote path when configured; simulation override |
| Config | Base URL, secret refs, bearer env, max page size (defaults empty/disabled) |
| Admin UI | Remote fetch enabled/configured on delta readiness card |

## Non-goals

- Production scheduled delta sync
- Provider mutation (POST/PATCH/PUT/DELETE)
- User/group write or deprovision from missing delta
- Raw SCIM body / token / Authorization in API or logs

Detay: [`phase-117-summary.md`](phase-117-summary.md).
