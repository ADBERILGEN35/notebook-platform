# Faz 116: SCIM Delta Rate-limit / Retry-After Hardening

## Hedef

Faz 115 delta POC için güvenli rate-limit, Retry-After parsing, timeout/backoff diagnostics ve provider error classification. Production scheduler ve IdP mutation yok.

## Scope

| Bileşen | Açıklama |
|---------|----------|
| `ScimRetryAfterParser` | seconds + HTTP-date + cap + invalid fallback |
| `ScimProviderResponseClassifier` | 429/5xx/401/403/timeout/bad JSON → error class |
| `ScimDeltaFetchDiagnosticsService` | Simulated probe (default); remote fetch **disabled** |
| API | Genişletilmiş readiness + dry-run rate-limit alanları |
| `scim_sync_runs` | Metadata via `last_error_code` + bounded `last_error_summary` |
| Config | `SCIM_DELTA_REMOTE_FETCH_ENABLED=false`, timeout/backoff caps |

## HTTP davranış tablosu (diagnostic)

| Durum | Error class | Warning (örnek) |
|-------|-------------|-----------------|
| 429 | `RATE_LIMITED` | `SCIM_DELTA_RATE_LIMITED` |
| Retry-After parse OK | — | `SCIM_DELTA_RETRY_AFTER_OBSERVED` |
| Retry-After > max | — | `SCIM_DELTA_RETRY_AFTER_CAPPED` |
| 502/503/504 | `PROVIDER_UNAVAILABLE` | `SCIM_DELTA_PROVIDER_UNAVAILABLE` |
| 401/403 | `PROVIDER_AUTH_FAILED` | `SCIM_DELTA_PROVIDER_AUTH_FAILED` |
| Timeout (simulated) | `TIMEOUT` | `SCIM_DELTA_PROVIDER_TIMEOUT` |
| Bad response (simulated) | `PROVIDER_BAD_RESPONSE` | `SCIM_DELTA_PROVIDER_BAD_RESPONSE` |
| Remote off | `REMOTE_FETCH_DISABLED` | `SCIM_DELTA_REMOTE_FETCH_DISABLED` |

Retryable outcomes also emit `SCIM_DELTA_BACKOFF_RECOMMENDED`.

## Non-goals

- Production scheduled sync
- Automatic retry loop (suggestion only)
- Raw Retry-After header / response body in API
- IdP mutation

Detay: [`phase-116-summary.md`](phase-116-summary.md).
