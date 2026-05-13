# Faz 99 Özeti: Platform Retention Dry-run Counts for Content-service

Spec: [`phase-99.md`](phase-99.md). Önceki faz: Faz 98 platform-wide retention governance foundation.

## 1. Yapılanlar

Faz 98'de kurulan platform retention governance foundation'ı content-service tarafında somut, aggregate-only ve legal-hold aware dry-run count desteğiyle tamamlandı. Content-service yeni bir internal admin endpoint sunar (`GET /internal/admin/retention/content/plan`); gateway platform planner content-service'ten count'ları çekip identity-service'in plan response'una merge eder. Frontend platform retention sayfası content count, blocked, capped ve unavailable state'lerini render eder. Destructive purge endpoint'i veya scheduler eklenmedi. Note title/body, contentBlocks, comment body, user email ve workspace/note/user id response/log/metric'lerden tamamen dışlandı.

## 2. Backend Değişiklikleri

### content-service

Yeni paket `com.notebook.lumen.content.admin.retention`:

- `ContentRetentionTargetKey` enum — bounded target list (`content.note_versions`, `content.comments`, `content.search_documents` DRY_RUN_READY; `content.notes` INVENTORY_ONLY).
- `ContentRetentionTargetStatus` enum.
- `ContentRetentionLegalHoldScope` enum + scope→blocking mapping (`ALL_PLATFORM`, `CONTENT` full block; `WORKSPACE`/`NOTE`/`USER` warning-only).
- `ContentRetentionProperties` (`@ConfigurationProperties content.retention`) — dry-run flag, cap, retention day defaults.
- `ContentRetentionAdminProperties` (`@ConfigurationProperties content.retention.admin`) — service JWT trust config.
- `ContentRetentionPlanDtos` — aggregate-only `ContentRetentionPlanResponse` ve `ContentRetentionTargetView` record'ları.
- `ContentRetentionCountRepository` — JdbcTemplate ile cutoff + indexed column tabanlı count. Her sorgu `LIMIT cap+1` ile bounded. Sadece `created_at` okunur; note body / comment body / content blocks asla scan edilmez.
- `ContentRetentionPlanService` — target loop, legal-hold uygulaması, capped warning, bounded metrics, audit publish.
- `ContentRetentionAdminAuthorizer` — service JWT verify, `internal:admin:retention:read` scope.
- `InternalContentRetentionController` — `GET /internal/admin/retention/content/plan?dryRun=true&target=&legalHoldScopes=&generatedAt=`. `dryRun=false` 400 ile reddedilir.

DB migration: `V14__add_retention_count_indexes.sql` — `idx_note_versions_created_at`, `idx_comments_created_at`.

Application bootstrap: `ContentServiceApplication` yeni iki property class'ını `@EnableConfigurationProperties` listesine ekledi.

### api-gateway

- `GatewayContentRetentionProperties` (`gateway.admin.platform-retention.content.*`) — enabled/url/timeoutMs.
- `ContentRetentionClient` — service JWT imzalı GET, `legalHoldScopes` query param ile.
- `AdminPlatformRetentionProxyService.plan()` rewrite:
  1. Identity plan + identity legal-holds paralel fetch.
  2. Aktif `ACTIVE` hold scope'larını çıkar.
  3. `contentRetentionClient.enabled()` ise content plan endpoint'ini çağır, hold scope listesini geçir.
  4. Content target satırlarını platform plan'a key-bazlı merge et (`eligibleCount`, `purgeableCount`, `blockedByLegalHold`, `status`, `warnings` overrides).
  5. Content unavailable → `CONTENT_RETENTION_SERVICE_UNAVAILABLE` plan warning; included → `PLATFORM_RETENTION_CONTENT_PLAN_INCLUDED` warning.
- Static helpers (`extractHoldScopes`, `mergeContentPlan`, `addWarning`) test-edilebilirlik için package-private.
- `ApiGatewayApplication` property class kaydı.

Gateway content-retention internal endpoint'i public route olarak açılmadı; sadece gateway server-side çağırıyor.

## 3. Frontend Değişiklikleri

- `features/admin/platform-retention-api.ts`: `retentionPlanTargetSchema.cutoff` (opsiyonel) eklendi; mevcut `eligibleCount` / `purgeableCount` / `blockedByLegalHold` / `warnings` / `status` alanları zaten vardı, ekstra alan eklenmedi.
- `pages/admin/AdminPlatformRetentionPage.tsx`:
  - Dry-run plan tablosuna `Status` kolonu (`DRY_RUN_READY` / `INVENTORY_ONLY` badge'leri).
  - `WarningChips` komponenti — `CONTENT_RETENTION_LEGAL_HOLD_BLOCKED` / `CONTENT_RETENTION_SERVICE_UNAVAILABLE` kırmızı, `CONTENT_RETENTION_QUERY_CAPPED` / `CONTENT_RETENTION_PARTIAL_LEGAL_HOLD_MAPPING` amber, `CONTENT_RETENTION_TARGET_INVENTORY_ONLY` / `..._DRY_RUN_DISABLED` slate tone.
  - `Blocked (...)` cell `activeHoldKeys` boşken parantezsiz "Blocked" render eder.
- Purge action eklenmedi; legal hold create/release zaten yalnız `admin:retention:legal-hold:write` permission'ı olanlar için gözükür.

## 4. Security/Privacy

- Content-service plan endpoint aggregate-only. `ContentRetentionPlanResponse` ve `ContentRetentionTargetView` yalnız count, status, cutoff, warning kodlarını taşır. Note title / body / contentBlocks / comment body / user email / workspace name response, log ve metric label'larında yok.
- Service JWT zorunlu (`internal:admin:retention:read` scope). Auth eksik → 401 `INTERNAL_AUTH_REQUIRED`, scope eksik → 403 `RETENTION_ACCESS_DENIED`, geçersiz → 401 `INVALID_SERVICE_JWT`.
- `dryRun=false` 400 ile reddediliyor. Destructive endpoint mevcut değil.
- Gateway `/admin/retention/platform/plan` mevcut `admin:retention:read` permission gate'ini kullanmaya devam ediyor; content enrichment platform planın güvenlik sınırını değiştirmiyor.
- Audit metadata yalnız target key, count'lar, capped flag, warning count, error class sembolü içerir (PII yok).
- RLS: Retention count cross-workspace aggregate gerektirir. Production'da DBA tarafından konfigure edilen RLS bypass (BYPASSRLS role / `row_security=off`) gerekir. Dev/Testcontainers `APP_RLS_ENABLED=false` default'u ile direkt çalışır. `docs/platform-retention-governance.md` faz 99 bölümünde belgelendi.

## 5. Tests

| Komut | Sonuç |
|---|---|
| `./gradlew :content-service:test --tests ContentRetentionPlanServiceTest --tests InternalContentRetentionControllerTest` | 12 passed |
| `./gradlew :api-gateway:test --tests AdminPlatformRetentionProxyServiceTest --tests AdminPlatformRetentionControllerTest` | 6 passed |
| `cd frontend && npm test -- --run platform-retention-api.test AdminPlatformRetentionPage.test` | 6 passed |
| `cd frontend && npx tsc -b` | exit 0 |

Coverage:

- Service: dry-run disabled boş response + warning, üç DRY_RUN_READY target'ın eligible+purgeable count'u, `ALL_PLATFORM` / `CONTENT` scope full block, `WORKSPACE` partial mapping warning-only, capped count warning + counter, target filter.
- Controller: missing auth, `dryRun=false` rejection, unknown target rejection, geçerli plan, `WORKSPACE:<id>` parse → scope head extraction.
- Gateway: `extractHoldScopes` ACTIVE filter, `mergeContentPlan` field override + warning union (non-content target'a dokunmaz), `addWarning` dedup.
- Frontend: cutoff + DRY_RUN_READY status, content-enriched plan response schema, mevcut blocked/release UI testleri korunuyor.

Spotless: WSL/Windows path-mismatch nedeniyle `:spotlessJava` task hata veriyor (`/home/adberilgen/.gradle/daemon/.../C:\Users\...\src`). Bu environment-level sorun; kod hataları değil. `./gradlew --stop` ardından da daemon farklı yoldan boot oluyor. Spotless gradle config'in WSL setup'ı gözden geçirilmeli (rollover ayrı PR olabilir).

## 6. Config/Deployment

### Content-service (`content-service/src/main/resources/application.yml`)

```
content.retention:
  dry-run-enabled: ${CONTENT_RETENTION_DRY_RUN_ENABLED:false}
  max-count-query-limit: ${CONTENT_RETENTION_MAX_COUNT_QUERY_LIMIT:100000}
  note-version-retention-days: ${CONTENT_RETENTION_NOTE_VERSION_RETENTION_DAYS:365}
  comment-retention-days: ${CONTENT_RETENTION_COMMENT_RETENTION_DAYS:365}
  search-document-retention-days: ${CONTENT_RETENTION_SEARCH_DOCUMENT_RETENTION_DAYS:90}
  include-archived-notes-only: ${CONTENT_RETENTION_INCLUDE_ARCHIVED_NOTES_ONLY:false}
  admin:
    kid / public-key / public-key-path / issuer / audience / clock-skew-seconds / allowed-scopes
```

### Api-gateway (`api-gateway/src/main/resources/application.yml`)

```
gateway.admin.platform-retention.content:
  enabled: ${CONTENT_RETENTION_INTEGRATION_ENABLED:false}
  url: ${CONTENT_RETENTION_INTERNAL_URL:http://content-service:8083}
  timeout-ms: ${CONTENT_RETENTION_INTERNAL_TIMEOUT_MS:3000}
```

### Helm (`deploy/helm/notebook-platform`)

`values.yaml`: yeni 7 `config.contentRetention*` alanı (defaults conservative — `false`/safe).

`templates/configmap.yaml`: 7 yeni env var: `CONTENT_RETENTION_DRY_RUN_ENABLED`, `CONTENT_RETENTION_INTEGRATION_ENABLED`, `CONTENT_RETENTION_INTERNAL_URL`, `CONTENT_RETENTION_INTERNAL_TIMEOUT_MS`, `CONTENT_RETENTION_MAX_COUNT_QUERY_LIMIT`, `CONTENT_RETENTION_NOTE_VERSION_RETENTION_DAYS`, `CONTENT_RETENTION_COMMENT_RETENTION_DAYS`, `CONTENT_RETENTION_SEARCH_DOCUMENT_RETENTION_DAYS`.

Helm content/gateway deployment'ları zaten configmap'ı `envFrom` ile alıyor; ekstra deployment-spec wiring gerekmedi.

GitOps env overrides eklenmedi (defaults `false` zaten conservative).

## 7. Eklenen/Düzenlenen Dosyalar

### Backend (content-service)

- `content-service/src/main/java/com/notebook/lumen/content/admin/retention/ContentRetentionTargetKey.java` (yeni)
- `content-service/src/main/java/com/notebook/lumen/content/admin/retention/ContentRetentionTargetStatus.java` (yeni)
- `content-service/src/main/java/com/notebook/lumen/content/admin/retention/ContentRetentionLegalHoldScope.java` (yeni)
- `content-service/src/main/java/com/notebook/lumen/content/admin/retention/ContentRetentionProperties.java` (yeni)
- `content-service/src/main/java/com/notebook/lumen/content/admin/retention/ContentRetentionAdminProperties.java` (yeni)
- `content-service/src/main/java/com/notebook/lumen/content/admin/retention/ContentRetentionPlanDtos.java` (yeni)
- `content-service/src/main/java/com/notebook/lumen/content/admin/retention/ContentRetentionCountRepository.java` (yeni)
- `content-service/src/main/java/com/notebook/lumen/content/admin/retention/ContentRetentionPlanService.java` (yeni)
- `content-service/src/main/java/com/notebook/lumen/content/admin/retention/ContentRetentionAdminAuthorizer.java` (yeni)
- `content-service/src/main/java/com/notebook/lumen/content/admin/retention/InternalContentRetentionController.java` (yeni)
- `content-service/src/main/java/com/notebook/lumen/content/ContentServiceApplication.java` (güncelleme)
- `content-service/src/main/resources/application.yml` (güncelleme)
- `content-service/src/main/resources/db/migration/V14__add_retention_count_indexes.sql` (yeni)
- `content-service/src/test/java/com/notebook/lumen/content/admin/retention/ContentRetentionPlanServiceTest.java` (yeni)
- `content-service/src/test/java/com/notebook/lumen/content/admin/retention/InternalContentRetentionControllerTest.java` (yeni)

### Backend (api-gateway)

- `api-gateway/src/main/java/com/notebook/lumen/gateway/config/GatewayContentRetentionProperties.java` (yeni)
- `api-gateway/src/main/java/com/notebook/lumen/gateway/admin/retention/ContentRetentionClient.java` (yeni)
- `api-gateway/src/main/java/com/notebook/lumen/gateway/admin/retention/AdminPlatformRetentionProxyService.java` (güncelleme — plan() rewrite + merge helpers)
- `api-gateway/src/main/java/com/notebook/lumen/gateway/ApiGatewayApplication.java` (güncelleme)
- `api-gateway/src/main/resources/application.yml` (güncelleme)
- `api-gateway/src/test/java/com/notebook/lumen/gateway/admin/retention/AdminPlatformRetentionProxyServiceTest.java` (yeni)

### Frontend

- `frontend/src/features/admin/platform-retention-api.ts` (güncelleme — `cutoff` field)
- `frontend/src/features/admin/platform-retention-api.test.ts` (güncelleme — content-enriched schema test)
- `frontend/src/pages/admin/AdminPlatformRetentionPage.tsx` (güncelleme — Status badge + WarningChips)

### Deploy

- `deploy/helm/notebook-platform/values.yaml` (güncelleme — 7 content retention config)
- `deploy/helm/notebook-platform/templates/configmap.yaml` (güncelleme — 7 yeni env var)

### Docs

- `docs/phases/phase-99.md` (Faz spec — bu fazın başında oluşturuldu)
- `docs/phases/phase-99-summary.md` (yeni — bu dosya)
- `docs/platform-retention-governance.md` (güncelleme — Faz 99 content-service integration bölümü)
- `CLAUDE.md` (güncelleme — Security & Privacy Sabitleri'ne 2 retention dry-run kuralı eklendi)

## 8. Kalan Açıklar

- **Spotless WSL path mismatch**: `:content-service:spotlessJava` task `/home/.../C:\Users\...` formatında bir path görüyor ve fail ediyor. Tests yeşil olduğu için kod doğru, ama CI spotlessCheck WSL ortamında geçici olarak engelliyor. Native Linux veya Windows-only çalıştırmada sorun yok.
- **RLS production readiness**: Cross-workspace count queries production'da BYPASSRLS role gerektirir; DBA setup için ayrı runbook PR'ı önerilir.
- **Retention admin service JWT trust**: `content.retention.admin.*` env defaults boş; production'a alınmadan önce signing key gateway'le coordinated rotate edilmeli (`docs/internal-service-auth.md` paterne uygun).
- **Content target registry sync**: Identity-service `RetentionTargetRegistry` Faz 98'den hâlâ `content.note_versions` / `content.comments` için `INVENTORY_ONLY` status'ünü taşıyor. Gateway merge content-service'ten DRY_RUN_READY status'unu override ediyor; ancak identity-service'in target inventory endpoint'i (`/targets`) hâlâ eski status'u dönüyor. Bu intentional değil — sonraki fazda identity-service registry status sync edilebilir veya enrichment `/targets` endpoint'ine de uygulanabilir.
- **GitOps env overrides**: `deploy/gitops/environments/{dev,staging,prod}` altında content retention override eklenmedi; defaults conservative olduğu için runtime üzerinde değişiklik yok. Enable rollout için ayrı GitOps PR önerilir.
- **content.search_documents semantik tartışması**: Hâlihazırda search-service tarafında `search.documents` target'ı var. Yeni `content.search_documents` content-service'in `search_index_outbox` üzerindeki view'i. İki target arasında semantic örtüşme olabilir; sonraki faz registry naming convention'ı netleştirebilir.
- **End-to-end integration test**: Gateway-content reactive Mono zip path tam integration test'i yazılmadı (mock-based static helper testleri var). Sonraki faza Playwright veya Spring webflux integration test eklenebilir.

## 9. Sonraki Faz Önerileri

1. **Identity-service registry status sync + GitOps rollout** — Faz 98 registry'sinde `content.note_versions`, `content.comments`, ve yeni `content.search_documents` target'larını DRY_RUN_READY'e geçir; dev environment'ta `CONTENT_RETENTION_INTEGRATION_ENABLED=true` rollout. Plan response artık tek source-of-truth görünür.
2. **Notification-service retention dry-run counts** — Aynı pattern (aggregate-only + legal-hold scope param + gateway merge) notification target'larına genişletilir; in_app/email/retention worker durumlarına gerçek count visibility kazandırır.
3. **Retention RLS production runbook** — DBA için BYPASSRLS role kurulumu, `row_security=off` ile çalışan dedicated retention admin connection, `INTERNAL_AUTH_MODE=service-jwt` ile coordinated key rotation runbook'u ve `rlsIntegrationTest` kapsamında retention path doğrulama. Production deployment'a zemin hazırlar.
