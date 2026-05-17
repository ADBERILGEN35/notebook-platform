# Faz 103 Özeti: Notification Retention RLS Production Runbook + Smoke Script

Önceki faz: [phase-102-summary.md](phase-102-summary.md). Faz spec: [phase-103.md](phase-103.md). Detay runbook: [`notification-retention-rls-production-runbook.md`](../notification-retention-rls-production-runbook.md), governance: [`platform-retention-governance.md`](../platform-retention-governance.md) → "Faz 103 Notification RLS Readiness".

## 1. Yapılanlar

Faz 102'de eklenen notification-service platform retention dry-run count akışının (`GET /internal/admin/retention/notification/plan`) production RLS ortamında güvenle açılabilmesi için operasyon dokümantasyonu, read-only preflight SQL ve smoke script eklendi. Faz 101 content retention RLS runbook pattern'i notification domain'ine uyarlandı; Faz 83 worker ile Faz 102 platform endpoint'i ayrımı operatör için netleştirildi.

Bu faz **production kodu değiştirmedi**: araştırmada `NOTIFICATION_RETENTION_DB_PERMISSION_DENIED` + `classifyDbFailure` (PermissionDeniedDataAccessException / SQLState `42501`) ve aggregate-only response garantisinin Faz 102'de zaten mevcut ve test kapsamında olduğu doğrulandı. `NOTIFICATION_RETENTION_RLS_NOT_READY` Faz 101'deki gibi dokümante edilmiş symbolic reserve olarak bırakıldı (backend emit etmez; smoke script readiness gap sinyali olarak yorumlar). Destructive purge, scheduler/worker değişikliği, notification data delete, Flyway migration ve endpoint contract değişikliği yapılmadı; prod default `false` korundu.

## 2. Backend değişiklikleri

Yok. Faz 102 guardrail davranışı doğrulamayla teyit edildi:

- `NotificationPlatformRetentionPlanService.classifyDbFailure` zaten `PermissionDeniedDataAccessException` / SQLState `42501` durumunu `NOTIFICATION_RETENTION_DB_PERMISSION_DENIED` symbolic koduna map ediyor; raw SQL/stack trace response/audit'e sızmıyor.
- `NotificationPlatformRetentionCountRepository` zaten bounded LIMIT pattern + indexed predicate kullanıyor; payload/body/recipient/email/workspaceId okumuyor.
- Mevcut testler bu davranışı kapsıyor (`dbPermissionDenied_mapsToSafeWarningWithoutRawSql`, `classifyDbFailure_detectsSqlState42501AndDefaultsToCountFailed`) — yeni test gerekmedi.

## 3. Frontend değişiklikleri

Yok. Frontend kapsam dışı; `AdminPlatformRetentionPage` veri-güdümlü olduğundan Faz 102'den beri notification satırlarını zaten render ediyor.

## 4. Security/Privacy

- Runbook ve scriptler secret içermez; password yalnız runbook `.md` içinde `<replace-with-secret>` placeholder (commit secret scan `*.md` exclude'lu, ek olarak gerçek değer yok).
- Preflight SQL read-only: yalnız `SELECT`/`SHOW`; DDL/DML/destructive ifade yok (scan ile doğrulandı).
- Smoke script raw payload/body/recipient/email/userId/workspaceId leak'i için response'u tarar; ihlalde exit 3 (privacy en yüksek öncelik, parse/shape hatasından önce raw text üzerinde kontrol edilir).
- Response/log/metric aggregate-only garantisi Faz 102'den korunur; bu faz yeni veri yüzeyi eklemez.
- DB permission hatası raw SQL olarak admin response'a dönmez (Faz 102 `classifyDbFailure`, doğrulandı).
- Production enable ayrı GitOps PR + governance change-request onayı gerektirir; prod default `false` korunur.
- `NOTIFICATION_RETENTION_RLS_NOT_READY` reserved symbol; backend aktif üretmez (Faz 101 `CONTENT_RETENTION_RLS_NOT_READY` paterni).

## 5. Tests

| Komut | Sonuç |
|---|---|
| `bash -n scripts/retention/notification-retention-dry-run-smoke.sh` | `BASH_SYNTAX_OK` |
| SQL destructive statement scan (`check-notification-retention-rls-readiness.sql`) | `NO_DESTRUCTIVE_STATEMENTS` |
| `bash scripts/check-no-secrets.sh` | `No obvious committed secrets detected.` |
| Embedded python `py_compile` | `PY_COMPILE_OK` |
| Smoke python fixture matrisi (8 senaryo) | ready+expect→0, gap+expect→2, gap+!expect→0, disabled+!expect→0, privacy→3, badjson→4, badshape→4, disabled+expect→2 — hepsi beklenen |
| `./gradlew :notification-service:test --tests "*platformretention*" --no-build-cache --no-configuration-cache` | BUILD SUCCESSFUL — PlanServiceTest 8 + ControllerTest 8 = 16, 0 failure/error/skip |

Not: `--no-build-cache --no-configuration-cache` kullanıldı (Windows-side Gradle'ın zehirlediği `C:\…` build-cache path sorununa karşı, önceki fazlardaki ortamsal kök neden). Spotless koşulmadı — yeni/değişen Java yok (kullanıcı onayıyla atlandı). Docker gerektiren Testcontainers/RLS testleri bu fazın kapsamında değil; platformretention testleri Docker'sız koştu.

## 6. Config/Deployment

Yeni env/Helm/GitOps değişikliği yok. Mevcut toggle'lar dokümante edildi:

- notification-service: `NOTIFICATION_RETENTION_DRY_RUN_COUNTS_ENABLED` (prod default `false`), `NOTIFICATION_RETENTION_MAX_COUNT_QUERY_LIMIT`, retention-day env'leri.
- gateway: `NOTIFICATION_RETENTION_INTEGRATION_ENABLED` (prod default `false`), `NOTIFICATION_RETENTION_INTERNAL_URL`, `NOTIFICATION_RETENTION_INTERNAL_TIMEOUT_MS`.
- Rollback: GitOps `notificationRetentionDryRunCountsEnabled` / `notificationRetentionIntegrationEnabled` → `"false"` (runbook bölüm 10).
- Dev/staging `"true"`, prod `"false"` (Faz 102'den değişmedi).

## 7. Eklenen/düzenlenen dosyalar

- **docs (yeni)**: `docs/notification-retention-rls-production-runbook.md` (12 bölüm), `docs/phases/phase-103-summary.md`.
- **docs (düzenlenen)**: `docs/platform-retention-governance.md` (Faz 103 bölümü), `docs/notification-retention-worker.md` (Faz 103 pointer + Faz 83/102 ayrım notu), `docs/production-readiness.md` (Faz 103 readiness checks + In-app notification center satırı), `docs/retention-rls-production-runbook.md` (notification runbook cross-link).
- **scripts (yeni)**: `scripts/retention/check-notification-retention-rls-readiness.sql` (read-only preflight), `scripts/retention/notification-retention-dry-run-smoke.sh` (gateway plan smoke).
- **backend/frontend/deploy**: değişiklik yok.

## 8. Kalan açıklar

- Faz 103 ayrı retention datasource binding eklemez; production'da retention role'e geçiş DBA + platform engineering tarafından datasource override / connection routing ile çözülür (Faz 101 ile aynı kısıt; runbook bölüm 4-5 referansı).
- `notification_dead_letter_requeue_requests.created_at` için adı dokümante edilmemiş index preflight'ta informational raporlanır (blocking değil); gerekirse gelecek fazda explicit index doğrulaması eklenebilir.
- Smoke/preflight production ortamına karşı bu fazda koşulmadı (ortam erişimi yok); prod enable öncesi runbook checklist'i uygulanmalı.
- `NOTIFICATION_FANOUT_SENT_RETENTION_DAYS` (platform, gün) ile Faz 83 `NOTIFICATION_FANOUT_SENT_RETENTION_HOURS` (worker, saat) ayrımı sürüyor (Faz 102'den taşındı).

## 9. Sonraki faz önerileri

1. Platform retention plan response'una servis-bazlı gruplama/özet (identity + content + notification) ve Grafana dashboard panel'i — operatör tek bakışta readiness/gap görür.
2. Workspace/search target'ları için aynı aggregate-only dry-run count katmanı (envanterde hâlâ `INVENTORY_ONLY`) — Faz 99/102 paterni.
3. Retention preflight + smoke'un CI'da Docker'lı staging job olarak otomatik koşulması (manuel checklist yerine gated kontrol).
