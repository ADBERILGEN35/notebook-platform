# Faz 102: Notification-service Retention Dry-run Counts

## Hedef

Notification-service retention target'ları için aggregate-only, legal-hold aware dry-run count desteği eklemek ve platform retention planına notification domain count görünürlüğü kazandırmak.

Bu fazda destructive purge, scheduler değişikliği veya gerçek delete yapılmayacak. Amaç, mevcut notification retention/legal hold altyapısını platform-wide retention governance ile hizalamak ve notification target'ları için güvenli count visibility sağlamaktır.

## Mevcut Durum

Faz 83'te notification retention worker foundation eklendi. Notification analytics, fanout outbox, dead-letter requeue requests, digest terminal data ve email notification terminal data için retention target'ları tanımlandı.

Faz 84'te notification legal hold modeli eklendi. Active legal hold, notification retention purge'ünü blokluyor.

Faz 98'de platform-wide retention governance foundation kuruldu.

Faz 99'da content-service için aggregate-only retention dry-run count endpoint'i eklendi.

Faz 100'de platform retention registry content target status'ları sync edildi.

Faz 101'de retention RLS production runbook ve DB permission guardrail'ları eklendi.

Kalan açık: notification-service retention target'ları platform-wide retention planında gerçek count visibility üretmiyor veya platform planner ile tam hizalı değil.

## Scope İçi

- Notification-service için internal retention dry-run plan endpoint'i eklemek veya mevcut retention planner'ı platform plan contract'ıyla hizalamak.
- Notification retention target'ları için aggregate-only eligible/purgeable count üretmek.
- Notification legal hold scope etkisini dry-run count planına yansıtmak.
- Gateway/platform planner ile notification-service count merge entegrasyonu eklemek.
- Platform retention UI'da notification target count, blocked, capped, unavailable state'lerini göstermek.
- Notification target registry status'larını platform inventory ile hizalamak.
- Audit, metrics, tests ve docs güncellemek.

## Scope Dışı

- Destructive purge.
- Retention worker davranışını değiştirme.
- Notification body/payload gösterme.
- Email body veya recipient email gösterme.
- User-level analytics.
- Object storage lifecycle.
- eDiscovery export.
- Tenant-specific retention policy.
- Yeni microservice.

## Notification Retention Targets

### DRY_RUN_READY

Aşağıdaki target'lar aggregate-only count üretebilir:

- `notification.analytics_hourly`
- `notification.fanout_outbox_sent`
- `notification.fanout_outbox_dead`
- `notification.dead_letter_requeue_requests`
- `notification.digest_items_terminal`
- `notification.email_notifications_terminal`

### INVENTORY_ONLY / DISABLED

Model yoksa veya mevcut tablolar projede yoksa target `INVENTORY_ONLY` veya `DISABLED` kalabilir:

- `notification.mobile_push`, varsa future
- `notification.webhook_delivery`, varsa future

## API / Şema

### Notification-service Internal API

```http
GET /internal/admin/retention/notification/plan
```

Query:

- `dryRun=true`
- `target` optional
- `legalHoldScopes` optional
- `generatedAt` optional

Auth:

- Service JWT
- Scope: `internal:admin:retention:read`

Response örneği:

```json
{
  "service": "notification-service",
  "dryRun": true,
  "targets": [
    {
      "targetKey": "notification.fanout_outbox_dead",
      "status": "DRY_RUN_READY",
      "defaultRetentionDays": 90,
      "cutoff": "2025-01-01T00:00:00Z",
      "eligibleCount": 42,
      "purgeableCount": 42,
      "blockedByLegalHold": false,
      "warnings": []
    }
  ]
}
```

Bu endpoint destructive method içermez.

## Legal Hold Mapping

Notification legal hold ve platform legal hold scope'ları dikkate alınmalıdır.

Full blocking scope'lar:

- `ALL_PLATFORM`
- `NOTIFICATION`
- Notification-domain `ALL_NOTIFICATION_RETENTION`, mevcutsa

Target-specific blocking:

- `FANOUT_OUTBOX`
- `DEAD_LETTER_REQUEUE_REQUESTS`
- `ANALYTICS`
- `DIGEST_ITEMS`
- `EMAIL_NOTIFICATIONS`

Davranış:

- Active hold varsa `eligibleCount` hesaplanabilir.
- `purgeableCount = 0`
- `blockedByLegalHold = true`
- Warning eklenir.
- Legal hold reason response'ta dönülmez.

## Platform Planner Integration

Gateway/platform retention plan, notification-service internal endpoint'ini çağırarak notification target'larını enrich eder.

Notification-service unavailable ise:

- Platform plan fail olmamalı.
- Notification target'ları unavailable warning ile dönmeli.
- UI partial data warning göstermeli.

Warning code önerileri:

- `NOTIFICATION_RETENTION_SERVICE_UNAVAILABLE`
- `NOTIFICATION_RETENTION_PARTIAL_COUNTS`
- `NOTIFICATION_RETENTION_QUERY_CAPPED`
- `NOTIFICATION_RETENTION_LEGAL_HOLD_BLOCKED`
- `NOTIFICATION_RETENTION_TARGET_INVENTORY_ONLY`
- `NOTIFICATION_RETENTION_DRY_RUN_DISABLED`

## Config

Notification-service:

```properties
NOTIFICATION_RETENTION_DRY_RUN_COUNTS_ENABLED=false
NOTIFICATION_RETENTION_MAX_COUNT_QUERY_LIMIT=100000
NOTIFICATION_ANALYTICS_RETENTION_DAYS=90
NOTIFICATION_FANOUT_SENT_RETENTION_DAYS=7
NOTIFICATION_FANOUT_DEAD_RETENTION_DAYS=90
NOTIFICATION_DEAD_LETTER_REQUEUE_REQUEST_RETENTION_DAYS=90
NOTIFICATION_DIGEST_SENT_RETENTION_DAYS=90
NOTIFICATION_EMAIL_TERMINAL_RETENTION_DAYS=90
```

Gateway:

```properties
NOTIFICATION_RETENTION_INTEGRATION_ENABLED=false
NOTIFICATION_RETENTION_INTERNAL_URL=http://notification-service:8084
NOTIFICATION_RETENTION_INTERNAL_TIMEOUT_MS=3000
```

Frontend:

- Mevcut `FRONTEND_PLATFORM_RETENTION_GOVERNANCE_ENABLED` kullanılabilir.

Helm/GitOps:

- Default conservative olmalı.
- Dev/staging enable ayrı override ile yapılabilir.
- Prod disabled kalmalı.

## Security / Privacy

- Aggregate-only response.
- Notification body yok.
- Raw payload yok.
- Email body yok.
- Recipient email yok.
- User id/email metric label olarak kullanılmaz.
- Token/cookie/header yok.
- Legal hold reason dönülmez.
- Service JWT zorunlu.
- Gateway admin permission gate korunur.
- Destructive endpoint yok.

## Audit Events

Notification-service:

- `NOTIFICATION_RETENTION_DRY_RUN_PLAN_GENERATED`
- `NOTIFICATION_RETENTION_DRY_RUN_FAILED`
- `NOTIFICATION_RETENTION_COUNT_CAPPED`

Gateway/platform:

- `PLATFORM_RETENTION_NOTIFICATION_PLAN_INCLUDED`
- `PLATFORM_RETENTION_NOTIFICATION_PLAN_UNAVAILABLE`

Audit metadata:

- `targetKey`
- `eligibleCount`
- `purgeableCount`
- `capped`
- `durationMs`
- `warningCount`

Yasak metadata:

- notification body
- raw payload
- email body
- recipient email
- user id/email
- token/header

## Metrics

Notification-service:

- `notification_retention_dry_run_total{target,result}`
- `notification_retention_eligible_count{target}`
- `notification_retention_count_duration_seconds{target}`
- `notification_retention_count_capped_total{target}`

Cardinality:

- `target` bounded registry key olmalı.
- `userId` / `email` / `recipient` / `workspaceId` label olamaz.

## Performance Guardrails

- Count query'leri indexed timestamp/status alanları üzerinden çalışmalı.
- Terminal statuses için status predicate kullanılmalı.
- Max count cap uygulanmalı.
- No JSON payload scan.
- No body scan.
- Query timeout guardrail olmalı.
- Cap aşılırsa capped warning dönmeli.

## Test Gereksinimleri

### Notification-service

- Analytics hourly eligible count cutoff'a göre hesaplanır.
- Fanout SENT eligible count cutoff/status'a göre hesaplanır.
- Fanout DEAD eligible count cutoff/status'a göre hesaplanır.
- Dead-letter requeue request count hesaplanır.
- Digest terminal count hesaplanır.
- Email terminal count hesaplanır, tablo/model varsa.
- Dry-run disabled güvenli response döner.
- Legal hold full block purgeable count'u 0 yapar.
- Target-specific hold ilgili target'ı bloklar.
- Query cap warning üretir.
- Service JWT zorunludur.
- Response raw payload/body/email içermez.
- Audit event yazılır.
- Metrics increment edilir.

### Gateway / Platform Planner

- Platform planner notification counts ile enrich edilir.
- Notification-service unavailable ise partial warning döner.
- Missing permission denied.
- Legal hold blocked state platform planında görünür.
- Destructive endpoint yoktur.

### Frontend

- Platform retention page notification eligible counts gösterir.
- `DRY_RUN_READY` badge gösterilir.
- Legal hold blocked badge gösterilir.
- Count capped warning gösterilir.
- Notification-service unavailable warning gösterilir.
- No purge action visible.

## Kabul Kriterleri

- Notification-service aggregate-only dry-run plan endpoint'i var.
- Platform retention plan notification target count'larıyla enrich ediliyor.
- Notification target status'ları registry/plan UI'da tutarlı.
- Active legal hold purgeable count'u 0'a düşürüyor.
- Response raw notification payload/body/email içermiyor.
- Destructive purge endpoint'i eklenmedi.
- Backend/gateway/frontend hedefli testleri eklendi.
- Docs ve phase summary güncellendi.
