# Faz 104: Platform Retention Service Summary + Dashboard Readiness

## Hedef

Platform retention plan response'una servis-bazlı özet görünürlüğü eklemek ve operatörlerin content / notification / identity retention readiness durumunu tek bakışta değerlendirebilmesini sağlamak.

Bu fazda destructive purge, scheduler, delete veya yeni retention target implementation yapılmayacak. Amaç, mevcut Faz 98–103 retention governance altyapısının gözlemlenebilirliğini artırmak ve admin UI / ops dashboard için aggregate summary contract oluşturmaktır.

## Mevcut Durum

Faz 98'de platform-wide retention governance foundation eklendi.

Faz 99'da content-service aggregate-only dry-run count endpoint'i eklendi.

Faz 100'de identity-service retention registry content target status'ları Faz 99 ile hizalandı.

Faz 101'de content retention RLS production runbook ve smoke/preflight guardrail'ları eklendi.

Faz 102'de notification-service aggregate-only dry-run count endpoint'i eklendi.

Faz 103'te notification retention RLS production runbook, smoke script ve preflight SQL eklendi.

Kalan açık: Platform retention plan response'u target listesi döndürüyor; ancak servis bazlı readiness/gap özetleri, dashboard-friendly durumlar ve operatör odaklı "hangi servis hazır, hangisi disabled/unavailable/blocked?" görünürlüğü sınırlı.

## Scope İçi

- Platform retention plan response'una servis-bazlı summary alanı eklemek.
- Content / notification / identity / audit-security domain'leri için aggregate status üretmek.
- Admin UI'da servis bazlı retention readiness kartları göstermek.
- Warning ve gap'leri servis bazında gruplayıp göstermek.
- Grafana/Prometheus dashboard readiness dokümantasyonu eklemek.
- Bounded-cardinality metrics eklemek veya mevcut metrics'i dashboard için dokümante etmek.
- Frontend ve gateway contract testlerini güncellemek.
- Docs ve phase summary güncellemek.

## Scope Dışı

- Destructive purge.
- Retention scheduler.
- Content / notification / audit / identity delete.
- Yeni target count implementation.
- Workspace/search target dry-run count.
- eDiscovery export.
- Tenant-specific retention policy.
- Yeni microservice.
- Gerçek Grafana dashboard JSON'unu zorunlu oluşturmak; ops doc yeterli olabilir.

## Response Contract

Platform retention plan response'una optional `serviceSummaries` alanı eklenir.

Örnek:

```json
{
  "generatedAt": "2026-05-16T10:00:00Z",
  "dryRun": true,
  "targets": [],
  "serviceSummaries": [
    {
      "service": "content-service",
      "dataClass": "CONTENT",
      "status": "READY",
      "totalTargets": 7,
      "dryRunReadyTargets": 3,
      "inventoryOnlyTargets": 4,
      "unavailableTargets": 0,
      "blockedTargets": 0,
      "cappedTargets": 0,
      "warningCount": 1,
      "warnings": ["PLATFORM_RETENTION_CONTENT_PLAN_INCLUDED"]
    },
    {
      "service": "notification-service",
      "dataClass": "NOTIFICATION",
      "status": "PARTIAL",
      "totalTargets": 6,
      "dryRunReadyTargets": 6,
      "inventoryOnlyTargets": 0,
      "unavailableTargets": 0,
      "blockedTargets": 1,
      "cappedTargets": 0,
      "warningCount": 2,
      "warnings": ["NOTIFICATION_RETENTION_LEGAL_HOLD_BLOCKED"]
    }
  ]
}
```

## Service Summary Status

Status enum önerisi:

- `READY`
- `PARTIAL`
- `DISABLED`
- `UNAVAILABLE`
- `BLOCKED_BY_HOLD`
- `INVENTORY_ONLY`
- `ERROR`

Status hesaplama önerisi:

- Service unavailable warning varsa `UNAVAILABLE`
- Tüm target'lar inventory-only ise `INVENTORY_ONLY`
- En az bir target legal hold ile blocked ise `BLOCKED_BY_HOLD`
- Dry-run ready target var ve fatal warning yoksa `READY`
- Karışık durumlarda `PARTIAL`
- Feature disabled ise `DISABLED`
- Unexpected error varsa `ERROR`

Eğer öncelik çakışırsa:

`ERROR > UNAVAILABLE > DISABLED > BLOCKED_BY_HOLD > PARTIAL > READY > INVENTORY_ONLY`

## Backend / Gateway

Gateway platform retention planner, merged target list'ten service summary üretmelidir.

Gerekenler:

- Target key prefix'inden service/domain mapping yapılabilir:
  - `content.*` → content-service
  - `notification.*` → notification-service
  - `identity.*` → identity-service
  - `audit.*` / `security.*` → identity-service veya audit-security, mevcut registry kararına göre
- Warning kodları service summary'ye dedupe edilerek taşınır.
- Summary raw domain data içermez.
- Missing content/notification integration durumları summary'ye yansır.
- Existing targets contract backward-compatible kalmalı.

## Frontend UI

`/app/admin/retention/platform` sayfasına service readiness kartları eklenir.

Kartlar:

- Content retention
- Notification retention
- Identity retention
- Audit/security retention

Her kart:

- Status badge
- Total targets
- Dry-run ready
- Inventory-only
- Blocked by legal hold
- Warnings count
- Last generated time
- Link / scroll anchor to target table filtered by service

UI state'leri:

- Ready
- Partial
- Disabled
- Unavailable
- Blocked by legal hold
- Inventory-only
- Error

No purge action visible.

## Metrics / Dashboard Readiness

Gateway veya admin aggregation layer'da bounded metrics önerilir:

- `platform_retention_service_summary_total{service,status}`
- `platform_retention_service_targets_total{service,targetStatus}`
- `platform_retention_service_warnings_total{service,warningCode}`

Cardinality:

- `service` bounded
- `status` bounded
- `warningCode` bounded known codes
- `userId` / `workspaceId` / `noteId` / `email` label yok

Eğer bu fazda metrics eklemek scope'u büyütürse, dashboard docs yeterlidir.

## Docs / Observability

Yeni veya güncellenecek doküman:

- `docs/platform-retention-dashboard-readiness.md`

İçerik:

- Service summary contract
- Status hesaplama
- UI interpretation
- Prometheus/Grafana panel önerileri
- Alert önerileri
- Runbook linkleri
- Production enable checklist ile ilişki

Grafana panel önerileri:

- Retention service readiness by service
- Dry-run ready target count
- Inventory-only target count
- Legal-hold blocked target count
- Service unavailable warning count
- Capped count warning count

Alert önerileri:

- content/notification integration expected=true ama service unavailable
- capped target count sürekli artıyor
- blocked-by-hold target sayısı beklenenden yüksek
- production'da feature enabled ama service summary unavailable

## Security / Privacy

- Summary aggregate-only olmalı.
- Note title/body, contentBlocks, comment body, notification payload/body, recipient email, user email yok.
- Legal hold reason dönülmez.
- Metrics high-cardinality label içermez.
- Admin permission gate değişmez.
- Destructive action yok.

## Test Gereksinimleri

### Api-gateway

- Service summary content target'larından doğru üretilir.
- Service summary notification target'larından doğru üretilir.
- Legal hold blocked target summary status'a yansır.
- Service unavailable warning summary status'a yansır.
- Warning dedupe çalışır.
- Existing plan target response bozulmaz.

### Frontend

- Service readiness kartları render edilir.
- `READY` / `PARTIAL` / `UNAVAILABLE` / `BLOCKED_BY_HOLD` / `DISABLED` status badge'leri doğru görünür.
- Warning count gösterilir.
- Target table mevcut davranışını korur.
- No purge action visible.

### Docs / Metrics

- Dashboard readiness doc eklenir.
- Eğer metric eklenirse unit test veya existing metrics pattern ile doğrulanır.

## Kabul Kriterleri

- Platform retention plan response optional `serviceSummaries` alanı döner.
- Existing targets contract backward-compatible kalır.
- Admin UI servis bazlı readiness kartları gösterir.
- Content ve notification service status'ları plan target'larına göre hesaplanır.
- Summary response raw domain data içermez.
- No destructive purge/action eklenmedi.
- Gateway/frontend testleri güncellendi.
- Observability/dashboard readiness docs eklendi.
- Faz sonu summary yazıldı.
