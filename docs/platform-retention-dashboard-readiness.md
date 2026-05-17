# Platform Retention Dashboard Readiness (Faz 104)

Faz 104 platform retention plan response'una servis-bazlı aggregate readiness özeti (`serviceSummaries`) ekler. Bu doküman summary contract'ını, status hesaplamasını, UI yorumunu ve operatör için Prometheus/Grafana panel + alert önerilerini tanımlar. Destructive purge, scheduler veya yeni count implementation bu fazda yoktur.

İlgili docs:

- [`platform-retention-governance.md`](platform-retention-governance.md) → "Faz 104 Service Summary"
- [`retention-rls-production-runbook.md`](retention-rls-production-runbook.md) (Faz 101 content RLS)
- [`notification-retention-rls-production-runbook.md`](notification-retention-rls-production-runbook.md) (Faz 103 notification RLS)
- [`production-readiness.md`](production-readiness.md) → "Faz 104 readiness checks"

## 1. Summary Contract

`GET /admin/retention/platform/plan?dryRun=true` mevcut `targets[]` + `warnings[]` alanlarına ek olarak **optional** `serviceSummaries[]` döner. Mevcut `targets` contract'ı değişmez (additive, backward-compatible).

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
    }
  ]
}
```

Alan anlamları:

| Alan | Anlam |
|---|---|
| `service` | Plan target'larının `service` alanından gruplanır (registry-authoritative; eksikse targetKey prefix'inden türetilir). |
| `dataClass` | targetKey prefix mapping: `content.*`→CONTENT, `notification.*`→NOTIFICATION, `identity.*`→IDENTITY, `audit.*`/`security.*`→AUDIT_SECURITY. |
| `status` | Servis readiness durumu (aşağıdaki precedence). |
| `totalTargets` | Servise ait plan target sayısı. |
| `dryRunReadyTargets` | `status=DRY_RUN_READY` target sayısı. |
| `inventoryOnlyTargets` | `status=INVENTORY_ONLY` target sayısı. |
| `unavailableTargets` | `status=UNAVAILABLE` target sayısı. |
| `blockedTargets` | `blockedByLegalHold=true` target sayısı. |
| `cappedTargets` | `*_QUERY_CAPPED` / `*_COUNT_CAPPED` warning'i olan target sayısı. |
| `warningCount` | Dedupe edilmiş warning sayısı. |
| `warnings` | Dedupe warning kodları (per-target + servise map'lenen plan-level prefix'li kodlar). |

Summary **aggregate-only**: note title/body, contentBlocks, comment body, notification payload/body, recipient/user email, workspace/note/user id veya legal hold reason **içermez**.

## 2. Status Hesaplama

Servis başına status precedence:

```
ERROR > UNAVAILABLE > DISABLED > BLOCKED_BY_HOLD > PARTIAL > READY > INVENTORY_ONLY
```

| Status | Tetik |
|---|---|
| `ERROR` | Warning `*_COUNT_FAILED` / `*_DB_PERMISSION_DENIED` / `*_DRY_RUN_FAILED`. |
| `UNAVAILABLE` | `unavailableTargets>0` veya warning `*_SERVICE_UNAVAILABLE`. |
| `DISABLED` | Warning `*_DRY_RUN_DISABLED` (feature flag kapalı). |
| `BLOCKED_BY_HOLD` | `blockedTargets>0` veya warning `*_LEGAL_HOLD_BLOCKED`. |
| `PARTIAL` | Hem dry-run ready hem inventory-only target var (karışık), ya da diğer kategoriler dışı belirsiz durum. |
| `READY` | Dry-run ready target var, fatal/gap warning yok, inventory-only yok. |
| `INVENTORY_ONLY` | Tüm target'lar inventory-only. |

Plan-level (global) warning'ler servise prefix mapping ile atanır: `CONTENT_RETENTION_*` / `PLATFORM_RETENTION_CONTENT_*` → content-service; `NOTIFICATION_RETENTION_*` / `PLATFORM_RETENTION_NOTIFICATION_*` → notification-service; `IDENTITY_RETENTION_*` / `AUDIT_RETENTION_*` / `PLATFORM_RETENTION_IDENTITY_*` → identity-service. Eşleşmeyen generic warning hiçbir servise yazılmaz.

## 3. UI Interpretation

`/app/admin/retention/platform` sayfası `serviceSummaries` varsa target table üstünde servis readiness kartları render eder. Her kart: service adı, dataClass, status badge, Total / Dry-run ready / Inventory only / Blocked by hold / Capped / Warnings sayıları, `generatedAt` ve dry-run target table'a anchor (`#platform-retention-dry-run`).

Badge tonları: `READY` yeşil; `PARTIAL` amber; `DISABLED`/`INVENTORY_ONLY` gri; `UNAVAILABLE`/`BLOCKED_BY_HOLD`/`ERROR` kırmızı. Kartlarda hiçbir purge/destructive aksiyon yoktur. `serviceSummaries` yoksa kart bölümü gizlenir; mevcut target/legal-hold tabloları davranışını korur.

Operatör yorumu:

- `READY` — servis dry-run count üretiyor, gap yok.
- `PARTIAL` — bir kısım target dry-run ready, bir kısmı hâlâ inventory-only; rollout devam ediyor.
- `INVENTORY_ONLY` — count integration henüz açılmamış; sadece envanter.
- `DISABLED` — feature flag kapalı (örn. `*_DRY_RUN_*_ENABLED=false`).
- `BLOCKED_BY_HOLD` — aktif legal hold purgeable count'ları sıfırlıyor (beklenen governance davranışı).
- `UNAVAILABLE` — gateway servis plan endpoint'ine ulaşamadı; identity planı korunur, gap incelenmeli.
- `ERROR` — count runtime/DB permission hatası; RLS readiness runbook'una bakılmalı.

## 4. Prometheus / Grafana Panel Önerileri

Faz 104 yeni gateway metric kodu eklemez; mevcut servis-bazlı metric'ler dashboard kaynağıdır (bounded-cardinality, `userId`/`email`/`recipient`/`workspaceId` label yok):

- content-service: `content_retention_dry_run_total{target,result}`, `content_retention_eligible_count{target}`, `content_retention_count_duration_seconds{target}`, `content_retention_count_capped_total{target}`
- notification-service: `notification_retention_dry_run_total{target,result}`, `notification_retention_eligible_count{target}`, `notification_retention_count_duration_seconds{target}`, `notification_retention_count_capped_total{target}`
- platform/identity: `platform_retention_plan_generated_total`, `platform_retention_targets_total{status,riskLevel}`, `platform_retention_blocked_by_hold_total{targetKey}`, `platform_legal_holds_active{scope}`

Önerilen paneller:

| Panel | Sorgu fikri |
|---|---|
| Retention service readiness by service | `platform_retention_targets_total` status bazında stacked; servis dağılımı için `content_retention_dry_run_total` / `notification_retention_dry_run_total` `result` bazında. |
| Dry-run ready target count | `count by (target)(content_retention_eligible_count) + count by (target)(notification_retention_eligible_count)`. |
| Inventory-only target count | `platform_retention_targets_total{status="INVENTORY_ONLY"}`. |
| Legal-hold blocked target count | `sum(platform_retention_blocked_by_hold_total)` ve `platform_legal_holds_active{scope}`. |
| Service unavailable warning count | `increase(content_retention_dry_run_total{result="error"}[15m])`, `increase(notification_retention_dry_run_total{result="error"}[15m])`. |
| Capped count warning count | `increase(content_retention_count_capped_total[1h])`, `increase(notification_retention_count_capped_total[1h])`. |

## 4a. Faz 105 Implemented Gateway Metrics

Faz 105 ile gateway aggregation layer (`PlatformRetentionMetricsPublisher`) `applyServiceSummaries` sonrası bounded-cardinality counter'ları emit eder. Tüm label'lar sabit allowlist'e map'lenir; allowlist dışı değer fallback'e düşer. Counter'lar plan generation başına artar (operatör `increase()` / `rate()` kullanır).

| Metric | Label'lar |
|---|---|
| `platform_retention_service_summary_total` | `service`, `status` |
| `platform_retention_service_targets_total` | `service`, `target_status` |
| `platform_retention_service_warnings_total` | `service`, `warning_code` |
| `platform_retention_plan_generated_total` | `result` (`success` / `error`) |
| `platform_retention_service_blocked_targets_total` | `service` |
| `platform_retention_service_capped_targets_total` | `service` |

Bounded label set:

- `service`: `content-service`, `notification-service`, `identity-service`, `platform`; allowlist dışı → `unknown`. **Not:** kod `audit.*`/`security.*` target'larını `identity-service`'e gruplar (registry-authoritative); `dataClass` yine `AUDIT_SECURITY` taşır ama metric `service` label'ı `identity-service`'tir — ayrı `audit-security` service label'ı yoktur.
- `status`: `READY`/`PARTIAL`/`DISABLED`/`UNAVAILABLE`/`BLOCKED_BY_HOLD`/`INVENTORY_ONLY`/`ERROR`; dışı → `unknown` (pratikte `computeServiceStatus` daima bu 7'den birini döner).
- `target_status`: `DRY_RUN_READY`/`INVENTORY_ONLY`/`PURGE_READY`/`DISABLED`/`ERROR`/`UNAVAILABLE`; dışı → `unknown`.
- `warning_code`: normalize edilmiş sınıf kümesi — `SERVICE_UNAVAILABLE`, `LEGAL_HOLD_BLOCKED`, `COUNT_CAPPED`, `DRY_RUN_DISABLED`, `COUNT_FAILED`, `PARTIAL_LEGAL_HOLD_MAPPING`, `PLAN_INCLUDED`; tanınmayan/serbest metin → `UNKNOWN_WARNING`. Ham/target-prefix'li kod label'a yazılmaz (cardinality bound).
- Forbidden: `userId`/`email`/`workspaceId`/`noteId`/`requestId`/raw `targetKey`/legal-hold key/raw warning message — hiçbiri label değildir.

Emission güvenliği: metric publish plan'ı mutate etmez, exception fırlatmaz (yutulur+log); plan generation başarısızsa yalnız `platform_retention_plan_generated_total{result="error"}` emit edilir (summary'siz). Response contract (`targets[]`/`serviceSummaries[]`) değişmez.

Grafana dashboard: [`observability/grafana/dashboards/platform-retention-readiness.json`](../observability/grafana/dashboards/platform-retention-readiness.json) (uid `platform-retention-readiness`). Panelleri yukarıdaki §4 önerilen panel listesini birebir bu implemented metric'ler üzerinden karşılar; runbook link'leri dashboard `links` ile gömülüdür.

## 5. Alert Önerileri

| Alert | Koşul |
|---|---|
| Beklenen servis unavailable | content/notification integration `expected=true` (GitOps `*Enabled="true"`) iken `*_dry_run_total{result="error"}` veya plan summary `status=UNAVAILABLE` sürüyor. |
| Capped count tırmanışı | `increase(*_count_capped_total[6h])` sürekli artıyor (cap aşımı kalıcı). |
| Beklenmeyen hold blokajı | `platform_retention_blocked_by_hold_total` veya summary `blockedTargets` beklenenden yüksek; legal hold scope incelenmeli. |
| Prod feature açık ama unavailable | Production'da feature enabled ama summary `status=UNAVAILABLE`/`ERROR` (RLS readiness gap). |

**Faz 105 implemented alerts:** [`observability/prometheus/alerts/platform-retention-readiness.yml`](../observability/prometheus/alerts/platform-retention-readiness.yml) (group `platform-retention-readiness.rules`, mevcut `notebook-platform-alerts.yml` dosyasına dokunmadan ayrı dosya):

- `PlatformRetentionServiceUnavailable` — `increase(platform_retention_service_summary_total{status="UNAVAILABLE"}[15m]) > 0`, `for: 15m`, severity `warning`.
- `PlatformRetentionCappedCounts` — `increase(platform_retention_service_warnings_total{warning_code="COUNT_CAPPED"}[6h]) > 0`, `for: 30m`, severity `warning`.
- `PlatformRetentionBlockedByLegalHoldHigh` — `increase(platform_retention_service_blocked_targets_total[1h]) > 0`, `for: 1h`, severity `info` (legal hold kasıtlı olabilir; sadece bilgilendirme, manuel eskalasyon).

## 6. Production Enable Checklist İlişkisi

Servis summary readiness, RLS production runbook checklist'lerinin gözlemlenebilir karşılığıdır:

- content-service prod enable öncesi: [`retention-rls-production-runbook.md`](retention-rls-production-runbook.md) §12 checklist; smoke exit 0 → summary `status` content-service için `READY`/`PARTIAL` beklenir.
- notification-service prod enable öncesi: [`notification-retention-rls-production-runbook.md`](notification-retention-rls-production-runbook.md) §11 checklist; `EXPECT_NOTIFICATION_RETENTION_READY=true` smoke exit 0 → summary notification-service için `READY`/`PARTIAL`.
- Prod default `false`; integration disabled iken summary servis için `DISABLED`/`INVENTORY_ONLY` beklenen normal durumdur, alert üretmez.

## 7. Güvenlik ve PII Sınırları

- Summary aggregate-only; raw domain data, recipient/user email, legal hold reason, high-cardinality id yok (gateway map serialization testi ile doğrulanır).
- Admin permission gate değişmez (`admin:retention:read`, `PLATFORM_RETENTION_GOVERNANCE_ENABLED`).
- Destructive aksiyon eklenmedi; plan dry-run only kalır.
- Faz 105 gateway aggregation metric'lerini ekler (§4a); tüm label'lar bounded allowlist, raw domain data / yüksek-cardinality id label yok. `SimpleMeterRegistry` testleri label disiplinini ve `unknown`/`UNKNOWN_WARNING` fallback'lerini doğrular.
