# Faz 99: Platform Retention Dry-run Counts for Content-service

## Hedef

Faz 98’de kurulan platform-wide retention governance foundation’ını content-service tarafında güvenli, read-only ve legal-hold aware dry-run count desteğiyle somutlaştırmak.

Bu fazda amaç, destructive purge yapmadan platform retention ekranında content domain için gerçek aggregate count görünürlüğü sağlamaktır. Note, notebook, workspace, comment, version veya audit verisi silinmeyecek; sadece uygun target’lar için eligible/purgeable count üretilecektir.

## Mevcut Durum

Faz 98’de platform-wide retention governance foundation tamamlandı:

- Platform retention target registry eklendi.
- Platform legal hold modeli eklendi.
- Platform retention dry-run plan UI eklendi.
- Destructive purge/scheduler eklenmedi.
- Target status değerleri tanımlandı:
  - `INVENTORY_ONLY`
  - `DRY_RUN_READY`
  - `PURGE_READY`
  - `DISABLED`
- Data class’lar tanımlandı:
  - `CONTENT`
  - `IDENTITY`
  - `AUDIT_SECURITY`
  - `NOTIFICATION`
- Çoğu platform target’ı hâlâ `INVENTORY_ONLY`.

Kalan açık: content-service target’ları için gerçek `eligibleCount` / `purgeableCount` hesaplanmıyor veya sınırlı. Legal hold scope’ları content dry-run count’larına tam bağlanmış değil.

## Scope İçi

- Content-service için retention dry-run count endpoint’i eklemek.
- Content retention target’larını `DRY_RUN_READY` ve `INVENTORY_ONLY` olarak netleştirmek.
- Count query’lerini güvenli ve performans kontrollü tasarlamak.
- Platform retention planner ile content-service dry-run planını entegre etmek.
- Legal hold scope mapping’i content target’ları için uygulamak.
- Frontend platform retention ekranında content count, blocked, capped ve unavailable state’lerini göstermek.
- Audit event ve bounded-cardinality metrics eklemek.
- Helm/GitOps config defaults eklemek.
- Backend/gateway/frontend testlerini eklemek.
- Dokümantasyonu güncellemek.

## Scope Dışı

- Actual content purge.
- Audit purge.
- Workspace/note/notebook/comment/version delete.
- Archive-before-delete.
- Object storage lifecycle.
- eDiscovery export.
- Tenant-specific retention policies.
- Per-workspace count dashboard.
- Service Worker remote-write sync.
- Real-time collaboration / CRDT.
- Workspace notification policy analytics.
- Break-glass provider secret rotation automation.
- Yeni microservice.

## Content Retention Targets

### DRY_RUN_READY

Aşağıdaki target’lar aggregate-only count üretebilir:

- `content.note_versions`
- `content.comments`
- `content.search_documents`

Gerekçe:

- `note_versions` ve `comments` için cutoff tabanlı görünürlük retention planning açısından değerlidir.
- `search_documents` türev veri olduğu için dry-run visibility açısından daha düşük risklidir.

### INVENTORY_ONLY

Aşağıdaki target’lar bu fazda sadece inventory olarak kalır:

- `content.workspaces`
- `content.notebooks`
- `content.notes`
- `content.attachments`

Gerekçe:

- Workspace/notebook/note deletion yüksek risklidir.
- Attachment/object storage lifecycle bu fazın dışında kalır.

## API / Şema

### Content-service Internal API

Yeni internal endpoint:

```http
GET /internal/admin/retention/content/plan
```

Query:

- `dryRun=true`
- `target` optional
- `generatedAt` optional

Auth:

- Service JWT
- Scope: `internal:admin:retention:read`

Response örneği:

```json
{
  "service": "content-service",
  "dryRun": true,
  "targets": [
    {
      "targetKey": "content.note_versions",
      "status": "DRY_RUN_READY",
      "defaultRetentionDays": 365,
      "cutoff": "2025-01-01T00:00:00Z",
      "eligibleCount": 1200,
      "purgeableCount": 1200,
      "blockedByLegalHold": false,
      "warnings": []
    }
  ]
}
```

Bu endpoint destructive method içermez.

### Platform Planner Integration

Platform retention planı content-service internal endpoint’ini çağırarak content target’larını enrich eder.

Content-service unavailable ise:

- Platform plan başarısız olmamalı.
- Content target’ları partial/unavailable warning ile dönmeli.
- UI’da “content-service unavailable” veya “partial counts” state’i gösterilmeli.

Warning code önerileri:

- `CONTENT_RETENTION_SERVICE_UNAVAILABLE`
- `CONTENT_RETENTION_PARTIAL_COUNTS`
- `CONTENT_RETENTION_QUERY_CAPPED`
- `CONTENT_RETENTION_TARGET_INVENTORY_ONLY`
- `CONTENT_RETENTION_LEGAL_HOLD_BLOCKED`
- `CONTENT_RETENTION_PARTIAL_LEGAL_HOLD_MAPPING`

### Legal Hold Mapping

Platform legal hold scope’ları content targets’a etki etmelidir.

#### Full blocking

Aşağıdaki scope’lar tüm content target’larını bloklar:

- `ALL_PLATFORM`
- `CONTENT`

Davranış:

- `eligibleCount` hesaplanabilir.
- `purgeableCount = 0`
- `blockedByLegalHold = true`
- Warning eklenir.

#### Partial / warning-only mapping

Aşağıdaki scope’lar bu fazda warning-only kalabilir:

- `WORKSPACE`
- `NOTE`
- `USER`

Davranış:

- `WORKSPACE` / `NOTE` hold varsa response warning üretir.
- Performanslı partial count mümkünse ileride `blockedCount` eklenebilir.
- `USER` scope comment author gibi privacy/performance riski taşıdığı için bu fazda warning-only kalır.

## Config

Content-service config:

```
CONTENT_RETENTION_DRY_RUN_ENABLED=false
CONTENT_RETENTION_MAX_COUNT_QUERY_LIMIT=100000
CONTENT_RETENTION_NOTE_VERSION_RETENTION_DAYS=365
CONTENT_RETENTION_COMMENT_RETENTION_DAYS=365
CONTENT_RETENTION_SEARCH_DOCUMENT_RETENTION_DAYS=90
CONTENT_RETENTION_INCLUDE_ARCHIVED_NOTES_ONLY=false
```

Gateway/platform config:

```
CONTENT_RETENTION_INTERNAL_URL=
CONTENT_RETENTION_INTERNAL_TIMEOUT_MS=3000
```

Frontend:

- Mevcut `FRONTEND_PLATFORM_RETENTION_GOVERNANCE_ENABLED` flag’i kullanılabilir.
- Yeni frontend flag zorunlu değil.

Helm/GitOps:

- Defaults conservative olmalı.
- Dry-run enabled default `false` kalmalı.

## Security / Privacy

- Response aggregate-only olmalı.
- Note title dönülmemeli.
- Note body / contentBlocks dönülmemeli.
- Comment body dönülmemeli.
- User email dönülmemeli.
- Workspace name gibi tanımlayıcılar mümkünse dönülmemeli.
- Raw audit metadata dönülmemeli.
- No destructive operation.
- Service JWT zorunlu.
- Gateway admin permission zorunlu.
- Metrics label’larında `workspaceId`, `noteId`, `userId`, `email` kullanılmamalı.

## Audit Events

Content-service:

- `CONTENT_RETENTION_DRY_RUN_PLAN_GENERATED`
- `CONTENT_RETENTION_DRY_RUN_FAILED`
- `CONTENT_RETENTION_COUNT_CAPPED`

Platform/gateway:

- `PLATFORM_RETENTION_CONTENT_PLAN_INCLUDED`
- `PLATFORM_RETENTION_CONTENT_PLAN_UNAVAILABLE`

Audit metadata:

- `targetKey`
- `eligibleCount`
- `purgeableCount`
- `capped`
- `durationMs`
- `warningCount`

Yasak metadata:

- note title
- note body
- contentBlocks
- comment body
- user email
- raw audit metadata

## Metrics

Content-service:

- `content_retention_dry_run_total{target,result}`
- `content_retention_eligible_count{target}`
- `content_retention_count_duration_seconds{target}`
- `content_retention_count_capped_total{target}`

Cardinality:

- `target` bounded registry key olmalı.
- `workspaceId` / `noteId` / `userId` / `email` label olarak kullanılmamalı.

## Performance Guardrails

- Count query’leri indexed columns üzerinden çalışmalı.
- Cutoff timestamp kullanılmalı.
- Max count cap uygulanmalı.
- Query timeout guardrail olmalı.
- No full content JSON scan.
- No note body scan.
- No comment body scan.
- Cap aşılırsa capped warning dönmeli.

## Test Gereksinimleri

### Content-service

- `content.note_versions` eligible count cutoff’a göre hesaplanır.
- `content.comments` eligible count cutoff’a göre hesaplanır.
- `content.search_documents` eligible count cutoff’a göre hesaplanır.
- Workspace/notebook/note/attachment targets inventory-only döner.
- Dry-run disabled ise güvenli disabled response döner.
- `ALL_PLATFORM` hold content targets’ı bloklar.
- `CONTENT` hold content targets’ı bloklar.
- `WORKSPACE` / `NOTE` scoped hold warning üretir.
- Service JWT zorunludur.
- Response raw note/comment content içermez.
- Query cap warning üretir.
- Audit event yazılır.
- Metrics increment edilir.

### Gateway / Platform Planner

- Platform planner content counts ile enrich edilir.
- Content-service unavailable ise partial warning döner.
- Missing permission denied.
- Legal hold blocked state platform planında görünür.
- Destructive endpoint yoktur.

### Frontend

- Platform retention page content eligible counts gösterir.
- `DRY_RUN_READY` badge gösterilir.
- `INVENTORY_ONLY` badge korunur.
- Content-service unavailable warning gösterilir.
- Legal hold blocked badge gösterilir.
- Count capped warning gösterilir.
- No purge action visible.

## Kabul Kriterleri

- Content-service aggregate-only dry-run plan endpoint’i eklendi.
- Platform retention plan content target count’larıyla enrich ediliyor.
- `content.note_versions`, `content.comments`, `content.search_documents` için count visibility var.
- Workspace/notebook/note/attachment inventory-only kalıyor.
- `ALL_PLATFORM` ve `CONTENT` legal hold scope’ları content purgeable count’u 0’a düşürüyor.
- Response raw content/comment/email içermiyor.
- Destructive purge endpoint’i eklenmedi.
- Frontend content count, blocked, capped ve unavailable state’lerini gösterebiliyor.
- Backend/gateway/frontend hedefli testleri eklendi.
- Docs ve config güncellendi.
