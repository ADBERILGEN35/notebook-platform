# Faz 117 Özeti: SCIM Delta Read-only Remote Fetch POC

Önceki faz: [phase-116-summary.md](phase-116-summary.md). Faz spec: [phase-117.md](phase-117.md).

## 1. Yapılanlar

Read-only remote fetch adapter foundation eklendi. `SCIM_DELTA_REMOTE_FETCH_ENABLED=true` ve tam runtime config (base URL + bearer) ile manual dry-run tek sayfalık **GET** yapabilir; yanıt sanitize edilerek yalnızca aggregate diagnostic (`fetchedResourceCount`, `pageObserved`, `nextCursorPresent`, rate-limit alanları) döner.

### Net sınırlar

| Soru | Cevap |
|------|--------|
| Production scheduler? | **Hayır** |
| IdP / provider mutation? | **Hayır** |
| Remote fetch default? | **Disabled** |
| HTTP methods? | **GET only** (POST/PATCH/PUT/DELETE yok) |
| DB user/group mutation? | **Hayır** |
| Raw payload/token/header in API? | **Hayır** |

## 2. Backend değişiklikleri

**Yeni (`scim.sync.delta`):** `ScimDeltaProviderClient`, `DefaultScimDeltaProviderClient`, `ScimDeltaProviderRequest`, `ScimDeltaProviderFetchResult`, `ScimDeltaProviderRequestBuilder`, `ScimDeltaProviderResponseSanitizer`, `ScimDeltaHttpMethod`, `ScimDeltaRemoteFetchWarnings`

**Güncellenen:** `ScimProperties` (+remote URL/token/max page), `ScimDeltaFetchDiagnosticsService`, `ScimDeltaRateLimitDiagnostics`, `ScimDeltaRunMetadataCodec`, `ScimDeltaSyncPocService`, `ScimSyncDiagnosticsDtos`

Dry-run davranışı:

- `enabled=false` → simulated/disabled warnings (`SCIM_DELTA_REMOTE_FETCH_DISABLED`)
- `enabled=true` eksik config → no HTTP (`SCIM_DELTA_REMOTE_FETCH_NOT_CONFIGURED`)
- `enabled=true` tam config → GET + sanitized aggregate
- Simulation flags → Faz 116 simulated path (network yok)

## 3. Frontend değişiklikleri

- `scimDeltaReadinessSchema` — `remoteFetchConfigured`, `remoteFetchAttempted`, `fetchedResourceCount`, `pageObserved`, `nextCursorPresent`
- `ScimDeltaReadinessCard` — remote configured/attempted/count/cursor (no secrets)

## 4. Security/Privacy

- Bearer token yalnızca server-side env (`SCIM_DELTA_REMOTE_BEARER_TOKEN`); Helm secret name/key refs UI için “configured” sinyali; token frontend/API/audit/log’a gitmez.
- Response body parse sonrası atılır; API’de raw SCIM yok.
- `last_error_summary` — symbolic metadata (fetchedResourceCount, nextCursorPresent, retry fields).

## 5. Tests / doğrulama

| Komut | Sonuç |
|---|---|
| `./gradlew :identity-service:test --tests "*Scim*" --tests "*Delta*" --tests "*Provider*" --tests "*Retry*"` | **PASS** (57 tests) |
| `./gradlew :api-gateway:test --tests "*Scim*" --tests "*Identity*"` | **PASS** |
| `cd frontend && npm test -- --run scim-diagnostics-api` | **SKIP** — Windows host: `vitest` not on PATH / deps not installed |
| `cd frontend && npx tsc -b` | **SKIP** — aynı ortam (frontend toolchain kurulu değil) |
| `bash scripts/check-no-secrets.sh` | **PASS** |

Yeni testler: `ScimDeltaProviderResponseSanitizerTest`, `DefaultScimDeltaProviderClientTest`, `ScimDeltaRemoteFetchDiagnosticsTest`; güncellenen fetch/POC testleri.

## 6. Config/Deployment

```yaml
SCIM_DELTA_REMOTE_FETCH_ENABLED: false
SCIM_DELTA_REMOTE_BASE_URL: ""
SCIM_DELTA_REMOTE_TOKEN_SECRET_NAME: ""
SCIM_DELTA_REMOTE_TOKEN_SECRET_KEY: ""
SCIM_DELTA_REMOTE_MAX_PAGE_SIZE: 100
# Runtime bearer (not in Git): SCIM_DELTA_REMOTE_BEARER_TOKEN
```

Helm `values.yaml` + identity `configmap.yaml` güncellendi.

## 7. Rate-limit entegrasyonu (Faz 116)

Remote GET sonuçları `ScimProviderResponseClassifier` ve `ScimRetryAfterParser` üzerinden sınıflandırılır (429/5xx/timeout/bad JSON). `retryAfterSeconds`, `nextRecommendedAttemptAt`, `providerErrorClass` dry-run/readiness ile uyumlu.

## 8. Warning kodları (örnek)

- `SCIM_DELTA_REMOTE_FETCH_DISABLED`
- `SCIM_DELTA_REMOTE_FETCH_NOT_CONFIGURED`
- `SCIM_DELTA_REMOTE_FETCH_ATTEMPTED`
- `SCIM_DELTA_PROVIDER_RESPONSE_SANITIZED`
- `SCIM_DELTA_RAW_PAYLOAD_SUPPRESSED`
- `SCIM_DELTA_NEXT_CURSOR_PRESENT`
- `SCIM_DELTA_REMOTE_PAGE_SIZE_CAPPED`

## 9. Sonraki adımlar

1. K8s secret → env injection operasyon runbook (bearer mount).
2. Multi-page cursor loop (still read-only, still manual).
3. Sandbox integration tests with redacted logging only.
