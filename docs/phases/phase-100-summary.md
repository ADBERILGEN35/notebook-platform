# Faz 100 Özeti: Platform Retention Registry Sync + GitOps Rollout

Spec: [`phase-100.md`](phase-100.md). Önceki faz: [Faz 99](phase-99-summary.md) — content-service aggregate-only retention dry-run count endpoint'i.

## 1. Yapılanlar

Faz 99'da content-service'e eklenen retention dry-run count endpoint'i ve gateway plan merge davranışı platform retention target registry (identity-service) ile hizalandı. `RetentionTargetRegistry` content-service tarafında dry-run hazır olan target'ları (`content.note_versions`, `content.comments`, `content.search_documents`) `DRY_RUN_READY` olarak işaretliyor; `/admin/retention/platform/targets` ve `/admin/retention/platform/plan` arasındaki content target status tutarsızlığı giderildi. Yeni `content.search_documents` registry satırı eklendi (content-side search outbox view). Dev/staging GitOps values content retention integration'ı aktif edecek şekilde güncellendi; prod açıkça disabled bırakıldı. Destructive purge endpoint'i eklenmedi.

## 2. Backend Değişiklikleri

### identity-service

- `RetentionTargetRegistry`:
  - `content.note_versions` status → `INVENTORY_ONLY` ⇒ `DRY_RUN_READY`.
  - `content.comments` status → `INVENTORY_ONLY` ⇒ `DRY_RUN_READY`.
  - Yeni satır `content.search_documents` (`content-service`, 90 gün, `DRY_RUN_READY`, `dryRunSupported=true`, `legalHoldSupported=true`, `destructivePurgeSupported=false`, `archiveRequiredBeforePurge=false`, risk `MEDIUM`).
  - `content.workspaces`, `content.notebooks`, `content.notes`, `content.attachments_media` `INVENTORY_ONLY` korundu.
  - Mevcut `search.documents` (search-service) registry satırına dokunulmadı — content-side `content.search_documents` ile yan yana kaldı.
- `content.workspaces` ve `content.notes` risk seviyeleri (`CRITICAL`) Faz 98'de bilinçli set edilmişti; phase spec'in HIGH önerisine rağmen blast-radius değerlendirmesi gereği `CRITICAL` korundu.
- Registry status'u `PlatformRetentionGovernanceService.toTarget()` ve `plan()` üzerinden hem `/targets` hem `/plan` response'una otomatik akıyor; ekstra wiring değişikliği gerekmedi.

### api-gateway

Kod değişikliği yok. `AdminPlatformRetentionProxyService.plan()` mevcut content-service plan merge davranışı korunur. `ContentRetentionClient` ve `GatewayContentRetentionProperties` dokunulmadı. Sadece yeni test case'leri eklendi.

### content-service

Kod değişikliği yok; Faz 99 endpoint contract'ı korunur.

## 3. Frontend Değişiklikleri

`AdminPlatformRetentionPage` render kodu değişmedi (`StatusBadge` zaten `DRY_RUN_READY` rengi yeşil emerald olarak renderliyor). Yalnız test güncellendi.

## 4. Security/Privacy

- Destructive purge endpoint eklenmedi.
- Registry/plan response raw content içermez (note title, body, contentBlocks, comment body, user email yok).
- Admin permission gate korundu: `admin:retention:read` ve `admin:retention:legal-hold:write`.
- Service JWT trust modeli değişmedi.
- Prod GitOps overrides conservative (false) bırakıldı; prod'a alma için ayrı approval gerekir.
- Metrics label'ı eklenmedi; high-cardinality identifier yok.
- Audit event behavior değişmedi (`PLATFORM_RETENTION_TARGETS_VIEWED`, `PLATFORM_RETENTION_PLAN_GENERATED` korunur).

## 5. Tests

| Komut | Sonuç |
|---|---|
| `./gradlew :identity-service:test --tests "com.notebook.lumen.identity.admin.retention.PlatformRetentionGovernanceServiceTest"` | passed (yeni 4 test + mevcut 6 = 10 toplam) |
| `./gradlew :api-gateway:test --tests "com.notebook.lumen.gateway.admin.retention.AdminPlatformRetentionProxyServiceTest"` | passed (yeni 2 test + mevcut 3 = 5 toplam) |
| `cd frontend && npm test -- --run AdminPlatformRetentionPage.test platform-retention-api.test` | 7 passed |
| `cd frontend && npx tsc -b` | exit 0 |
| `bash scripts/helm-template-check.sh` | helm CLI yok → script tarafından graceful skip |

Coverage:

- Identity: registry content target'larının `DRY_RUN_READY` (3) ve `INVENTORY_ONLY` (4) status doğrulaması, content target'larının `destructivePurgeSupported=false` + `legalHoldSupported=true`, `DRY_RUN_READY` content target'larının `dryRunSupported=true`, `plan("content.note_versions", true, ...)` response'unda `status=DRY_RUN_READY`.
- Gateway: `content.search_documents` merge override (status + eligibleCount + purgeableCount), identity target'larının (users + scim_sync_runs) content merge sonrası değişmemesi (regression guard).
- Frontend: `DRY_RUN_READY` badge inventory + plan tablolarında en az 2 kez render, `Unavailable` purge cell render, "Future gated" cell yok.

Spotless: WSL/Windows path-mismatch nedeniyle `spotlessJava` task `/mnt/c/users/...` (lowercase) vs `/mnt/c/Users/...` (proper case) path mismatch hatası veriyor — Faz 99 özetindeki environment-level sorun tekrar; kod kaynaklı değil.

## 6. Config/Deployment

### GitOps env overrides

**`deploy/gitops/environments/dev/values.yaml`** — `config:` block'una eklendi:

```yaml
contentRetentionDryRunEnabled: "true"
contentRetentionIntegrationEnabled: "true"
```

**`deploy/gitops/environments/staging/values.yaml`** — aynı eklendi.

**`deploy/gitops/environments/prod/values.yaml`** — açıkça disabled olarak set edildi:

```yaml
contentRetentionDryRunEnabled: "false"
contentRetentionIntegrationEnabled: "false"
```

Helm chart `values.yaml` defaults (`false`) ve `templates/configmap.yaml` env var wiring (Faz 99'dan beri mevcut) dokunulmadı.

### Çalışma davranışı (dev/staging enable sonrası)

- Content-service `ContentRetentionPlanService` count query'leri aktif.
- Gateway `ContentRetentionClient.enabled()` true → `/admin/retention/platform/plan` content-side enriched count döner.
- `/admin/retention/platform/targets` doğrudan registry'den content target status'larını `DRY_RUN_READY` veya `INVENTORY_ONLY` olarak gösterir.

## 7. Eklenen/Düzenlenen Dosyalar

### Backend (identity-service)

- `identity-service/src/main/java/com/notebook/lumen/identity/admin/retention/RetentionTargetRegistry.java` (güncelleme — 2 status flip + 1 yeni target)
- `identity-service/src/test/java/com/notebook/lumen/identity/admin/retention/PlatformRetentionGovernanceServiceTest.java` (güncelleme — 4 yeni test)

### Backend (api-gateway)

- `api-gateway/src/test/java/com/notebook/lumen/gateway/admin/retention/AdminPlatformRetentionProxyServiceTest.java` (güncelleme — 2 yeni test)

### Frontend

- `frontend/src/pages/admin/AdminPlatformRetentionPage.test.tsx` (güncelleme — 1 yeni test)

### Deploy

- `deploy/gitops/environments/dev/values.yaml` (güncelleme — 2 content retention env)
- `deploy/gitops/environments/staging/values.yaml` (güncelleme — 2 content retention env)
- `deploy/gitops/environments/prod/values.yaml` (güncelleme — 2 content retention env explicit disabled)

### Docs

- `docs/phases/phase-100.md` (faz spec — bu fazın başında oluşturuldu)
- `docs/phases/phase-100-summary.md` (yeni — bu dosya)
- `docs/platform-retention-governance.md` (güncelleme — Faz 100 bölümü + targets tablosu başlığı netleştirme)

## 8. Kalan Açıklar

- **`search.documents` vs `content.search_documents` naming**: İki target yan yana kalıyor; semantic örtüşme (search-service index vs content-side outbox view) consolidation gelecek faza bırakıldı.
- **Spotless WSL path mismatch**: Faz 99'dan beri tekrar eden environment-level sorun; CI Linux runner'larında bu hata yok.
- **Production rollout**: Prod default disabled. Prod'a alma için ayrı governance/change-request approval + RLS BYPASSRLS DBA setup gerekir.
- **End-to-end integration test**: Gateway + content reactive Mono zip path için full Spring webflux integration test yazılmadı; mock-based static helper testleri korunuyor.
- **Risk level alignment**: Spec `HIGH` öneren `content.workspaces` ve `content.notes` mevcut `CRITICAL` korundu; yeniden değerlendirmek isterse ayrı PR önerilir.

## 9. Sonraki Faz Önerileri

1. **Notification-service retention dry-run counts** — Aynı Faz 99 patterni (aggregate-only, legal-hold scope param, gateway merge) `notification.retention_targets` target'ına uygulanır; in_app/email retention worker'larına gerçek count visibility kazandırılır.
2. **Retention RLS production runbook + BYPASSRLS role** — DBA için `row_security=off` ile çalışan dedicated retention admin connection, service-jwt key rotation runbook'u ve `rlsIntegrationTest` kapsamında retention path doğrulama; production rollout'a zemin hazırlar.
3. **Search-target naming consolidation** — `search.documents` ve `content.search_documents` target'larını tek source-of-truth altında birleştir (search-service kaynaklı index vs content-service outbox view). Registry naming convention netleştirme + frontend label tutarlılığı.
