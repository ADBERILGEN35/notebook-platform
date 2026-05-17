# Faz 103: Notification Retention RLS Production Runbook + Smoke Script

## Hedef

Faz 102'de eklenen notification-service platform retention dry-run count akışının production RLS ortamında güvenli çalışabilmesi için runbook, smoke script, readiness checks ve dokümantasyon hazırlamak.

Bu fazda destructive purge, scheduler değişikliği veya notification data delete yapılmayacak. Amaç, notification retention dry-run count entegrasyonunun production enable önkoşullarını netleştirmek ve Faz 101 content retention RLS runbook pattern'ini notification domain'e uyarlamaktır.

## Mevcut Durum

Faz 102'de notification-service için yeni platform retention dry-run endpoint'i eklendi:

- `GET /internal/admin/retention/notification/plan`
- Service JWT scope: `internal:admin:retention:read`
- Aggregate-only response
- Raw notification body, payload, recipient email, user id/email response/log/metric içinde yok
- Gateway platform planner notification planını identity registry planına merge ediyor
- GitOps dev/staging enabled, prod disabled
- Destructive purge yok

Kalan açıklar:

- Notification için ayrı retention DB role / BYPASSRLS production enable önkoşulları Faz 101 runbook'una tam işlenmedi.
- Notification retention dry-run için smoke script yok.
- Faz 83 worker endpoint'i ile Faz 102 platform endpoint'i yan yana; operatör confusion riskine karşı runbook netliği artırılmalı.

## Scope İçi

- Notification retention production RLS enable runbook eki yazmak.
- Notification retention dry-run smoke script eklemek.
- Notification DB readiness SQL veya existing Faz 101 SQL'e notification table checks eklemek.
- Notification-service permission/DB failure warning davranışını dokümante etmek.
- Platform retention governance docs'unu notification production readiness ile güncellemek.
- Production enable checklist eklemek.
- Smoke script'in raw payload/recipient leak guardrail kontrolü yapmasını sağlamak.
- Hedefli script/doc testlerini çalıştırmak.

## Scope Dışı

- Destructive purge.
- Retention worker davranışını değiştirme.
- Notification data delete.
- Flyway migration.
- Yeni microservice.
- Notification endpoint contract değişikliği.
- User-level analytics.
- Object storage lifecycle.
- eDiscovery export.
- Tenant-specific retention policy.

## Runbook

Yeni veya güncellenecek doküman:

- `docs/notification-retention-rls-production-runbook.md`

İçerik:

1. Amaç ve kapsam
2. Faz 83 worker retention ile Faz 102 platform dry-run farkı
3. Production RLS problemi
4. Dedicated retention DB role önerisi
5. BYPASSRLS / `row_security=off` yaklaşımı
6. Minimum DB privilege set'i
7. Notification tabloları için readiness checks
8. Service JWT trust gereksinimi
9. Smoke script kullanımı
10. Rollback / disable adımları
11. Prod enable checklist
12. Security/privacy guardrail listesi

## SQL Readiness Checks

Faz 101'deki script genişletilebilir veya notification-specific yeni script eklenebilir.

Önerilen yeni script:

- `scripts/retention/check-notification-retention-rls-readiness.sql`

Kontroller:

- Current DB user
- `row_security` durumu
- BYPASSRLS capability, varsa
- Notification retention target tabloları mevcut mu?
- Count için gerekli timestamp/status kolonları var mı?
- İlgili indexler var mı?
- Bounded count probe çalışıyor mu?
- Query sadece aggregate count döndürüyor mu?

Script destructive işlem yapmamalı.

## Smoke Script

Yeni script:

- `scripts/retention/notification-retention-dry-run-smoke.sh`

Kontroller:

- Gateway `/admin/retention/platform/plan` çağrısı
- Notification target'ları response içinde görünüyor mu?
  - `notification.analytics_hourly`
  - `notification.fanout_outbox_sent`
  - `notification.fanout_outbox_dead`
  - `notification.dead_letter_requeue_requests`
  - `notification.digest_items_terminal`
  - `notification.email_notifications_terminal`
- Target status `DRY_RUN_READY`
- `eligibleCount` / `purgeableCount` alanları numeric veya null-safe
- `blockedByLegalHold` alanı mevcut
- Raw payload/body/email/recipient leak yok
- `NOTIFICATION_RETENTION_SERVICE_UNAVAILABLE` varsa exit code ile readiness gap belirtiliyor

Script secret içermemeli. Token/base URL env ile alınmalı.

Önerilen env:

```bash
API_BASE_URL=
ADMIN_ACCESS_TOKEN=
EXPECT_NOTIFICATION_RETENTION_READY=false
```

Exit code önerisi:

- `0`: smoke başarılı
- `2`: service unavailable / integration disabled readiness gap
- `3`: privacy guardrail violation
- `4`: schema/shape mismatch

## Backend Guardrails

Eğer eksikse minimal iyileştirme yapılabilir:

- Notification DB permission error safe warning:
  - `NOTIFICATION_RETENTION_DB_PERMISSION_DENIED`
- RLS/readiness warning reserve:
  - `NOTIFICATION_RETENTION_RLS_NOT_READY`
- Raw SQL exception response/audit'e sızmamalı.

Faz 102'de bu davranış büyük ölçüde varsa sadece test/doc doğrulaması yeterlidir.

## Config

Prod default disabled kalmalı:

```properties
NOTIFICATION_RETENTION_DRY_RUN_COUNTS_ENABLED=false
NOTIFICATION_RETENTION_INTEGRATION_ENABLED=false
```

Dev/staging:

```properties
NOTIFICATION_RETENTION_DRY_RUN_COUNTS_ENABLED=true
NOTIFICATION_RETENTION_INTEGRATION_ENABLED=true
```

Production enable önkoşulları:

- Dedicated DB role veya DBA-approved RLS bypass hazır.
- Service JWT trust config hazır.
- SQL readiness script geçti.
- Smoke script geçti.
- Observability/alerting hazır.
- Rollback env toggle hazır.
- GitOps PR onaylandı.

## Security / Privacy

- Runbook/script secret içermez.
- SQL script destructive işlem yapmaz.
- Smoke script raw payload/body/email/recipient leak kontrolü yapar.
- Response/log/metric içinde notification body, raw payload, email body, recipient email, userId/email yok.
- DB permission error raw SQL/stack trace olarak admin response'a dönmez.
- Production enable ayrı GitOps PR ve onay gerektirir.
- Prod default disabled korunur.

## Test Gereksinimleri

### Notification-service

- DB permission exception safe warning'e map ediliyorsa test korunur veya eklenir.
- Raw SQL exception response'a sızmaz.
- Existing `platformretention.*` testleri bozulmaz.

### Scripts

- `scripts/retention/notification-retention-dry-run-smoke.sh` için `bash -n` geçmeli.
- SQL script destructive keyword scan'den geçmeli.
- Scriptler secret hardcode etmemeli.
- Mevcut `scripts/check-no-secrets.sh` geçmeli.

### Docs

- `docs/platform-retention-governance.md` notification RLS production readiness link'i içermeli.
- `docs/notification-retention-worker.md` Faz 83 worker endpoint'i ile Faz 102 platform endpoint'i ayrımını açıklamalı.
- `docs/production-readiness.md` notification retention readiness checklist içermeli.

## Kabul Kriterleri

- Notification retention production RLS runbook eklendi.
- Notification retention dry-run smoke script eklendi.
- Notification retention SQL readiness script eklendi veya Faz 101 script'i notification checks ile genişletildi.
- Prod default disabled kaldı.
- Scriptler secret içermiyor.
- Raw payload/body/email leak guardrail smoke script'te kontrol ediliyor.
- Destructive purge/delete eklenmedi.
- Docs güncellendi.
- Hedefli script/backend doğrulamaları çalıştırıldı veya environment kısıtı açık raporlandı.
