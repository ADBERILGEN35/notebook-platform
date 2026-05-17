# Faz 116 Özeti: SCIM Delta Rate-limit / Retry-After Hardening

Önceki faz: [phase-115-summary.md](phase-115-summary.md). Faz spec: [phase-116.md](phase-116.md).

## 1. Yapılanlar

SCIM delta POC için rate-limit / Retry-After / timeout / backoff **diagnostic foundation** eklendi. Retry-After parser (seconds + HTTP-date + cap), provider HTTP error classification, simulated dry-run probe (default **no remote fetch**), genişletilmiş readiness/dry-run API alanları ve sync-run metadata codec (symbolic only).

### Net sınırlar

| Soru | Cevap |
|------|--------|
| Production scheduler? | **Hayır** |
| IdP / provider mutation? | **Hayır** |
| Remote fetch default? | **Disabled** (`SCIM_DELTA_REMOTE_FETCH_ENABLED=false`) |
| Otomatik retry loop? | **Hayır** — `nextRecommendedAttemptAt` + warnings only |
| Raw Retry-After / response body in API? | **Hayır** |

## 2. Backend değişiklikleri

**Yeni:** `ScimRetryAfterParser`, `ScimProviderResponseClassifier`, `ScimProviderErrorClass`, `ScimDeltaFetchDiagnosticsService`, `ScimDeltaRunMetadataCodec`, `ScimDeltaRateLimitDiagnostics`

**Güncellenen:** `ScimProperties` (+4 config), `ScimDeltaSyncPocService`, `ScimSyncDiagnosticsDtos`, `ScimSyncDiagnosticsService.toRun` (+metadata decode)

Dry-run request (optional simulation, no network by default):

- `simulatedHttpStatus`, `simulatedRetryAfter`, `simulatedTimeout`, `simulatedBadResponse`

## 3. Frontend değişiklikleri

- `scimDeltaReadinessSchema` — rate-limit alanları
- `ScimDeltaReadinessCard` — remote fetch, timeout, backoff, provider error, Retry-After summary

## 4. Security/Privacy

- Raw Retry-After header değeri API’de dönmüyor (yalnızca bounded `retryAfterSeconds`).
- `last_error_summary` — symbolic key=value (max 512); token/PII/body yok.
- Audit metadata: error class + seconds only.
- Metric label’larda userId/email yok.

## 5. Tests / doğrulama

| Komut | Sonuç |
|---|---|
| `./gradlew :identity-service:test --tests "*Scim*" --tests "*Delta*" --tests "*Provider*" --tests "*Retry*"` | **PASS** |
| `./gradlew :api-gateway:test --tests "*Scim*"` | **PASS** |
| `npm test -- --run scim-diagnostics-api` | **SKIP** — WSL Node Vitest ESM |
| `npx tsc -b` (frontend) | **SKIP** — aynı ortam |
| `bash scripts/check-no-secrets.sh` | **PASS** |

Yeni: `ScimRetryAfterParserTest`, `ScimProviderResponseClassifierTest`, `ScimDeltaFetchDiagnosticsServiceTest`; güncellenen POC/sync testleri.

## 6. Config/Deployment

```yaml
SCIM_DELTA_REMOTE_FETCH_ENABLED: false
SCIM_DELTA_HTTP_TIMEOUT_MS: 3000
SCIM_DELTA_MAX_RETRY_AFTER_SECONDS: 300
SCIM_DELTA_BACKOFF_BASE_SECONDS: 30
```

Helm `values.yaml` + identity `configmap.yaml` güncellendi.

## 7. Eklenen/güncellenen dosyalar

- **Java (yeni):** parser, classifier, fetch diagnostics, metadata codec, error enum
- **Java (güncellenen):** `ScimDeltaSyncPocService`, `ScimProperties`, DTOs, `ScimSyncDiagnosticsService`
- **Test:** 3 yeni + POC test güncellemeleri
- **Frontend:** `scim-diagnostics-api.ts`, `AdminEnterprisePages.tsx`, schema test
- **Docs:** `phase-116.md`, `phase-116-summary.md`, `scim-sync-diagnostics.md`, `scim-delta-sync-design.md`, `scim-provider-compatibility.md`, `production-readiness.md`

## 8. Rate-limit davranış özeti

| Input | Diagnostic outcome |
|-------|-------------------|
| No simulation, remote off | `REMOTE_FETCH_DISABLED`, backoff base suggested |
| `simulatedHttpStatus=429` + `simulatedRetryAfter=120` | `RATE_LIMITED`, seconds=120, capped if >300 |
| `simulatedHttpStatus=503` | `PROVIDER_UNAVAILABLE`, backoff recommended |
| `simulatedTimeout=true` | `TIMEOUT` |
| `simulatedBadResponse=true` | `PROVIDER_BAD_RESPONSE` |

## 9. Sonraki adımlar

1. Tenant-specific remote fetch URL + certified retry policy (future phase).
2. Controlled automatic retry worker (explicit approval, not scheduler).
3. Integration tests against Okta/Entra sandboxes with redacted logs only.
