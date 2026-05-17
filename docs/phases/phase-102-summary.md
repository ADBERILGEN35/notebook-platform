# Faz 102 Özeti: Notification-service Retention Dry-run Counts

Önceki faz: [phase-101-summary.md](phase-101-summary.md). Detay tasarım/runbook: [platform-retention-governance.md](../platform-retention-governance.md) → "Faz 102 Notification-service Integration", [notification-retention-worker.md](../notification-retention-worker.md).

## 1. Yapılanlar

Notification-service retention target'larına platform-wide retention governance contract'ı için aggregate-only, legal-hold aware, dry-run-only count görünürlüğü eklendi. Mevcut Faz 83 retention worker ve Faz 84 legal hold davranışı bozulmadan, yan yana çalışan yeni bir platform retention katmanı (`/internal/admin/retention/notification/plan`) inşa edildi; gateway bu planı identity registry planına content merge'inden sonra ikinci aşamada merge eder. Destructive purge eklenmedi, yeni microservice/Flyway migration yok; feature flag prod'da default kapalı.

## 2. Backend değişiklikleri

**notification-service** — yeni paket `admin/platformretention/`:
- `InternalNotificationPlatformRetentionController` — `GET /internal/admin/retention/notification/plan` (service JWT scope `internal:admin:retention:read`, audience `notification-service`). `dryRun=false` → 400 `RETENTION_DRY_RUN_ONLY`; bilinmeyen target → 400 `RETENTION_TARGET_UNKNOWN`; `legalHoldScopes` CSV (suffix strip + unknown skip).
- `NotificationPlatformRetentionPlanService` — feature-flag erken dönüş, target loop, capped count, full/partial legal hold, `classifyDbFailure` (PermissionDenied/SQLState 42501 → güvenli warning), audit + metric emit.
- `NotificationPlatformRetentionCountRepository` — 6 capped count query (`SELECT count(*) FROM (SELECT 1 … WHERE <indexed pred> LIMIT cap+1) bounded`); Faz 83 indexleri reuse, yeni migration yok.
- `NotificationPlatformRetentionTargetKey` (6), `NotificationPlatformRetentionTargetStatus`, `NotificationPlatformRetentionLegalHoldScope`, `NotificationPlatformRetentionProperties`, `NotificationPlatformRetentionDtos`.
- `InternalNotificationAuthorizer` — `ADMIN_RETENTION_READ_SCOPE` sabiti. `NotificationServiceApplication` — properties registrasyonu. `application.yml` — `notification.platform-retention.*` binding + trusted-gateway-admin allowed-scopes.

**identity-service**: `RetentionTargetRegistry` placeholder `notification.retention_targets` satırı 6 spesifik target ile değiştirildi (`notification.analytics_hourly` LOW/90, `fanout_outbox_sent` MEDIUM/7, `fanout_outbox_dead` HIGH/90, `dead_letter_requeue_requests` MEDIUM/90, `digest_items_terminal` LOW/90, `email_notifications_terminal` HIGH/90; hepsi DRY_RUN_READY, destructivePurge=false).

**api-gateway**: `NotificationRetentionClient` + `GatewayNotificationRetentionProperties`; `AdminPlatformRetentionProxyService` iki-aşamalı merge (content sonrası notification), `mergeNotificationPlan` (`eligibleCount/purgeableCount/blockedByLegalHold/status/defaultRetentionDays/cutoff` override + warning dedupe-merge), `WARN_NOTIFICATION_UNAVAILABLE`/`WARN_NOTIFICATION_INCLUDED`; holds artık content **veya** notification enabled ise çekilir. `ApiGatewayApplication` + `application.yml` binding.

## 3. Frontend değişiklikleri

`AdminPlatformRetentionPage.tsx` veri-güdümlü olduğundan notification target satırları ve merge edilen plan otomatik render olur (şema değişikliği gerekmedi). `WARNING_TONE` map'ine 11 `NOTIFICATION_RETENTION_*` / `PLATFORM_RETENTION_NOTIFICATION_PLAN_INCLUDED` rengi eklendi (slate fallback yerine doğru ton). `platform-retention-api.ts` değişmedi.

## 4. Security/Privacy

- Endpoint service JWT + `internal:admin:retention:read` scope; eksik/yanlış scope → 401/403 (authorizer, controller test ile doğrulandı).
- Response/log/metric aggregate-only: target view sadece `targetKey/status/defaultRetentionDays/cutoff/eligibleCount/purgeableCount/blockedByLegalHold/warnings`. JSON serialization testi `email/recipient/subject/body/payload/workspaceId/userId` yokluğunu assert eder.
- Count query'leri indexed alan + cutoff timestamp + LIMIT cap; full body/JSON scan yok. Metric label'ları bounded (`target`, `result`); yasak label yok.
- Legal hold reason/metadata response'a yansımaz; full hold → `purgeableCount=0` + `blockedByLegalHold=true`.
- DB permission hatası raw SQL sızdırmadan `NOTIFICATION_RETENTION_DB_PERMISSION_DENIED`'e map edilir.
- Audit: `NOTIFICATION_RETENTION` aggregate üzerinden `DRY_RUN_PLAN_GENERATED`/`DRY_RUN_FAILED`/`COUNT_CAPPED`.
- RLS: notification tabloları cross-workspace aggregate; Faz 101 retention DB role / `row_security=off` koşulu geçerli, prod default `false`.

## 5. Tests

| Komut | Sonuç |
|---|---|
| `:notification-service:test --tests "...platformretention.*"` | BUILD SUCCESSFUL (PlanServiceTest 8 + ControllerTest 8) |
| `:notification-service:test` (full) | passed |
| `:identity-service:test --tests "*PlatformRetentionGovernanceServiceTest*"` | BUILD SUCCESSFUL (yeni `registryIncludesNotificationDryRunReadyTargets` dahil) |
| `:api-gateway:test --tests "*AdminPlatformRetentionProxyServiceTest" "*AdminPlatformRetentionControllerTest"` | BUILD SUCCESSFUL (4 yeni merge testi dahil) |
| `spotlessCheck` | passed |
| `cd frontend && npx tsc -b` | passed |
| `cd frontend && npm test -- --run AdminPlatformRetentionPage` | 4 passed (yeni notification render testi dahil) |

Not: `ApiGatewayIntegrationTest` ve diğer Testcontainers/RLS testleri Docker gerektirir; bu sandbox'ta Docker yok → environment-skipped (kod regresyonu değil, önceki fazlarla aynı kısıt). Spotless ilk koşuda Windows-side Gradle'ın zehirlediği build cache nedeniyle `C:\…` hedef path hatası verdi; `--no-build-cache --no-configuration-cache` ile temizlendi (kök neden ortamsal).

## 6. Config/Deployment

- notification-service `application.yml`: `notification.platform-retention.*` (env: `NOTIFICATION_RETENTION_DRY_RUN_COUNTS_ENABLED` default `false`, `NOTIFICATION_RETENTION_MAX_COUNT_QUERY_LIMIT` default `100000`, retention-day env'leri mevcut isimleri reuse).
- api-gateway `application.yml`: `gateway.admin.platform-retention.notification.{enabled,url,timeout-ms}` (env `NOTIFICATION_RETENTION_INTEGRATION_ENABLED` default `false`).
- Helm `values.yaml` + `configmap.yaml`: 6 yeni `config.notificationRetention*` default + env mapping.
- GitOps: `dev`/`staging` → `notificationRetentionDryRunCountsEnabled`/`notificationRetentionIntegrationEnabled` `"true"`; `prod` açıkça `"false"`.

## 7. Eklenen/düzenlenen dosyalar

- **backend (yeni)**: notification-service `admin/platformretention/` 8 sınıf; api-gateway `NotificationRetentionClient`, `GatewayNotificationRetentionProperties`.
- **backend (düzenlenen)**: `InternalNotificationAuthorizer`, `NotificationServiceApplication`, notification `application.yml`; `RetentionTargetRegistry`; `AdminPlatformRetentionProxyService`, `ApiGatewayApplication`, gateway `application.yml`.
- **test (yeni/düzenlenen)**: `NotificationPlatformRetentionPlanServiceTest`, `InternalNotificationPlatformRetentionControllerTest`, `AdminPlatformRetentionProxyServiceTest` (+4), `PlatformRetentionGovernanceServiceTest` (+1), `AdminPlatformRetentionPage.test.tsx` (+1).
- **frontend**: `AdminPlatformRetentionPage.tsx`.
- **docs**: `phase-102.md`, `phase-102-summary.md`, `platform-retention-governance.md`, `notification-retention-worker.md`.
- **deploy**: Helm `values.yaml`/`configmap.yaml`; GitOps `dev`/`staging`/`prod` `values.yaml`.

## 8. Kalan açıklar

- Docker olmayan ortamda Testcontainers/RLS entegrasyon testleri (`ApiGatewayIntegrationTest`, `rlsIntegrationTest`) koşulamadı; CI'da Docker'lı pipeline doğrulamalı.
- Notification için ayrı retention DB role / BYPASSRLS prod enable önkoşulu Faz 101 runbook'una bir cümle eklenebilir (bu fazda kapsam dışı; default `false`).
- Faz 83 `/internal/admin/notifications/retention/plan` ile Faz 102 platform endpoint'i yan yana; UI/operatör karışıklığına karşı not `notification-retention-worker.md`'ye eklendi.
- `NOTIFICATION_FANOUT_SENT_RETENTION_DAYS` (gün) Faz 83'teki `NOTIFICATION_FANOUT_SENT_RETENTION_HOURS` (saat) ile ayrı; platform katmanı gün bazlı, worker saat bazlı çalışmaya devam eder.

## 9. Sonraki faz önerileri

1. Notification retention RLS prod-enable runbook eki + smoke script (`notification-retention-dry-run-smoke.sh`) — Faz 101 content paterni.
2. Platform retention plan response'una servis-bazlı gruplama/özet (content+notification+identity) ve dashboard panel'i.
3. Workspace/search target'ları için aynı dry-run count katmanının değerlendirilmesi (envanterde hâlâ INVENTORY_ONLY).
