# Faz 106: Workspace/Search Retention Dry-run Counts

## Hedef

Workspace-service ve search-service retention target'ları için aggregate-only, dry-run-only count görünürlüğü eklemek ve platform retention planındaki `INVENTORY_ONLY` kalan son domain'leri daha operasyonel hale getirmek.

Bu fazda destructive purge, scheduler, workspace/note/search document delete veya tenant-specific retention enforcement yapılmayacak. Amaç, Faz 99 content-service ve Faz 102 notification-service pattern'ini workspace/search target'larına kontrollü şekilde uygulamaktır.

## Mevcut Durum

Faz 99'da content-service için aggregate-only retention dry-run counts eklendi.

Faz 102'de notification-service için aggregate-only retention dry-run counts eklendi.

Faz 104'te platform retention response'una `serviceSummaries` eklendi.

Faz 105'te retention readiness için bounded metrics, Grafana dashboard ve Prometheus alert dosyası eklendi.

Kalan açık: workspace/search target'ları hâlâ çoğunlukla `INVENTORY_ONLY`. Bu nedenle dashboard ve service summary, bazı domain'lerde gerçek dry-run count görünürlüğü sağlayamıyor.

## Scope İçi

- Workspace-service için güvenli dry-run count endpoint'i eklemek veya target'ları sınırlı şekilde DRY_RUN_READY yapmak.
- Search-service için aggregate-only dry-run count endpoint'i eklemek.
- Gateway platform retention planner'a workspace/search merge entegrasyonu eklemek.
- Identity retention registry target status'larını güncellemek.
- Platform retention UI'nın mevcut data-driven yapısıyla workspace/search target counts göstermesini sağlamak.
- Metrics/dashboard mevcut Faz 105 publisher'ı ile otomatik çalışacak şekilde target status/service summary uyumunu korumak.
- Docs, tests ve phase summary güncellemek.

## Scope Dışı

- Destructive purge.
- Workspace/notebook/tag/invitation delete.
- Search document delete.
- Tenant-specific retention policy.
- RLS modelini değiştirme.
- Object storage lifecycle.
- eDiscovery export.
- Yeni microservice.
- User-level analytics.
- Frontend yeni sayfa tasarımı.

## Workspace Retention Targets

### DRY_RUN_READY önerisi

- `workspace.invitations_expired`
- `workspace.audit_like_events`, model varsa
- `workspace.membership_inactive`, sadece model uygunsa ve aggregate-only count mümkünse

### INVENTORY_ONLY korunacaklar

- `workspace.workspaces`
- `workspace.notebooks`
- `workspace.tags`
- `workspace.memberships`

Gerekçe:

- Workspace/notebook/membership deletion yüksek riskli.
- Expired invitation gibi terminal/operasyonel veri count visibility için daha güvenli.

Eğer mevcut modelde expired invitation alanı net değilse workspace target'ları bu fazda `INVENTORY_ONLY` kalabilir; bu durumda yalnız search-service implementasyonu yapılabilir ve workspace için docs/registry netliği sağlanır.

## Search Retention Targets

### DRY_RUN_READY önerisi

- `search.documents_stale`
- `search.reindex_jobs_terminal`
- `search.indexing_failures_terminal`, model varsa

### INVENTORY_ONLY / DISABLED

- `search.documents_active`
- model karşılığı olmayan target'lar

Gerekçe:

- Search documents türev veri olduğu için dry-run count visibility düşük risklidir.
- Aktif index document delete bu fazın dışında kalır.

## API / Şema

### Workspace-service internal endpoint

```http
GET /internal/admin/retention/workspace/plan
```

Query:

- `dryRun=true`
- `target` optional
- `legalHoldScopes` optional
- `generatedAt` optional

Auth:

- Service JWT
- Scope: `internal:admin:retention:read`

### Search-service internal endpoint

```http
GET /internal/admin/retention/search/plan
```

Query:

- `dryRun=true`
- `target` optional
- `legalHoldScopes` optional
- `generatedAt` optional

Auth:

- Service JWT
- Scope: `internal:admin:retention:read`

Response contract content/notification pattern'iyle aynı olmalı:

```json
{
  "service": "search-service",
  "dryRun": true,
  "targets": [
    {
      "targetKey": "search.documents_stale",
      "status": "DRY_RUN_READY",
      "defaultRetentionDays": 90,
      "cutoff": "2025-01-01T00:00:00Z",
      "eligibleCount": 100,
      "purgeableCount": 100,
      "blockedByLegalHold": false,
      "warnings": []
    }
  ]
}
```

## Legal Hold Mapping

Full blocking scope'lar:

- `ALL_PLATFORM`

Domain blocking:

- `CONTENT` may block search if search documents are derived from content.
- `WORKSPACE` may block workspace targets.
- `NOTE` may block search targets only if note-level relationship is available.

Faz 106 önerisi:

- Search target'ları için `ALL_PLATFORM` ve `CONTENT` full block.
- Workspace target'ları için `ALL_PLATFORM` ve `WORKSPACE` full block.
- Fine-grained `NOTE` / `USER` mapping warning-only.

Legal hold reason response'a dönülmez.

## Gateway Integration

Gateway platform retention planner:

- Identity registry planını alır.
- Legal hold scopes çıkarır.
- Content plan merge eder.
- Notification plan merge eder.
- Workspace plan merge eder, enabled ise.
- Search plan merge eder, enabled ise.
- `serviceSummaries` yeniden hesaplanır.
- Faz 105 metrics mevcut summary'den publish edilir.

Service unavailable durumunda:

- Plan fail olmamalı.
- İlgili warning eklenmeli:
  - `WORKSPACE_RETENTION_SERVICE_UNAVAILABLE`
  - `SEARCH_RETENTION_SERVICE_UNAVAILABLE`

## Config

Workspace-service:

```properties
WORKSPACE_RETENTION_DRY_RUN_COUNTS_ENABLED=false
WORKSPACE_RETENTION_MAX_COUNT_QUERY_LIMIT=100000
WORKSPACE_RETENTION_EXPIRED_INVITATION_RETENTION_DAYS=90
```

Search-service:

```properties
SEARCH_RETENTION_DRY_RUN_COUNTS_ENABLED=false
SEARCH_RETENTION_MAX_COUNT_QUERY_LIMIT=100000
SEARCH_RETENTION_STALE_DOCUMENT_RETENTION_DAYS=90
SEARCH_RETENTION_TERMINAL_JOB_RETENTION_DAYS=90
```

Gateway:

```properties
WORKSPACE_RETENTION_INTEGRATION_ENABLED=false
WORKSPACE_RETENTION_INTERNAL_URL=http://workspace-service:8082
WORKSPACE_RETENTION_INTERNAL_TIMEOUT_MS=3000

SEARCH_RETENTION_INTEGRATION_ENABLED=false
SEARCH_RETENTION_INTERNAL_URL=http://search-service:8085
SEARCH_RETENTION_INTERNAL_TIMEOUT_MS=3000
```

Prod defaults disabled kalmalı.

## Warning Codes

Workspace:

- `WORKSPACE_RETENTION_SERVICE_UNAVAILABLE`
- `WORKSPACE_RETENTION_DRY_RUN_DISABLED`
- `WORKSPACE_RETENTION_QUERY_CAPPED`
- `WORKSPACE_RETENTION_LEGAL_HOLD_BLOCKED`
- `WORKSPACE_RETENTION_TARGET_INVENTORY_ONLY`
- `WORKSPACE_RETENTION_COUNT_FAILED`

Search:

- `SEARCH_RETENTION_SERVICE_UNAVAILABLE`
- `SEARCH_RETENTION_DRY_RUN_DISABLED`
- `SEARCH_RETENTION_QUERY_CAPPED`
- `SEARCH_RETENTION_LEGAL_HOLD_BLOCKED`
- `SEARCH_RETENTION_TARGET_INVENTORY_ONLY`
- `SEARCH_RETENTION_COUNT_FAILED`

## Security / Privacy

- Aggregate-only response.
- Workspace name yok.
- Notebook title yok.
- User email yok.
- Search document body/snippet yok.
- Query text yok.
- Raw indexed content yok.
- Legal hold reason yok.
- No destructive operation.
- Service JWT zorunlu.
- Gateway admin permission gate korunur.
- Metrics label'larında `workspaceId`, `userId`, `noteId`, `query`, `targetKey` gibi high-cardinality/sensitive alanlar yok.

## Audit Events

Workspace-service:

- `WORKSPACE_RETENTION_DRY_RUN_PLAN_GENERATED`
- `WORKSPACE_RETENTION_DRY_RUN_FAILED`
- `WORKSPACE_RETENTION_COUNT_CAPPED`

Search-service:

- `SEARCH_RETENTION_DRY_RUN_PLAN_GENERATED`
- `SEARCH_RETENTION_DRY_RUN_FAILED`
- `SEARCH_RETENTION_COUNT_CAPPED`

Gateway/platform:

- `PLATFORM_RETENTION_WORKSPACE_PLAN_INCLUDED`
- `PLATFORM_RETENTION_WORKSPACE_PLAN_UNAVAILABLE`
- `PLATFORM_RETENTION_SEARCH_PLAN_INCLUDED`
- `PLATFORM_RETENTION_SEARCH_PLAN_UNAVAILABLE`

Audit metadata aggregate-only olmalı.

## Metrics

Service-local metrics opsiyonel ama tercih edilir:

Workspace-service:

- `workspace_retention_dry_run_total{target,result}`
- `workspace_retention_eligible_count{target}`
- `workspace_retention_count_duration_seconds{target}`
- `workspace_retention_count_capped_total{target}`

Search-service:

- `search_retention_dry_run_total{target,result}`
- `search_retention_eligible_count{target}`
- `search_retention_count_duration_seconds{target}`
- `search_retention_count_capped_total{target}`

Gateway Faz 105 metrics mevcut `serviceSummaries` üzerinden otomatik çalışmalı.

## Performance Guardrails

- Count query indexed timestamp/status alanları üzerinden çalışmalı.
- Max count cap uygulanmalı.
- No JSON/body/snippet scan.
- No full text content scan.
- Search index body veya tsvector içerikleri okunmamalı.
- Query timeout guardrail olmalı.
- Cap aşılırsa capped warning dönmeli.

## Test Gereksinimleri

### Workspace-service

- Eğer DRY_RUN_READY target varsa cutoff'a göre eligible count hesaplanır.
- Inventory-only target'lar doğru döner.
- Dry-run disabled güvenli response döner.
- Legal hold full block purgeable count'u 0 yapar.
- Service JWT zorunludur.
- Response workspace/user PII içermez.
- Query cap warning üretir.

### Search-service

- Stale/terminal search target eligible count hesaplanır.
- Inventory-only target'lar doğru döner.
- Dry-run disabled güvenli response döner.
- Legal hold full block purgeable count'u 0 yapar.
- Service JWT zorunludur.
- Response raw indexed content/snippet/query içermez.
- Query cap warning üretir.

### Gateway

- Workspace/search plan merge çalışır.
- Service unavailable warning üretir.
- `serviceSummaries` workspace/search için doğru status üretir.
- Faz 105 metrics bounded label'larla workspace/search service değerlerini yayınlar.
- Existing content/notification behavior bozulmaz.

### Frontend

- Workspace/search target count ve status'ları data-driven render edilir.
- Warning tones gerekiyorsa eklenir.
- Service readiness cards workspace/search service summary gösterir.
- No purge action visible.

## Kabul Kriterleri

- Search-service için en az bir DRY_RUN_READY target aggregate-only count üretir.
- Workspace-service için ya güvenli DRY_RUN_READY target eklenir ya da inventory-only karar docs/test ile netleştirilir.
- Gateway platform plan workspace/search target'ları merge edebilir.
- `serviceSummaries` workspace/search durumunu gösterir.
- Faz 105 metrics workspace/search service label'larını bounded şekilde kapsar.
- Response raw workspace/search content veya PII içermez.
- Destructive purge/delete eklenmedi.
- Backend/gateway/frontend testleri güncellendi.
- Docs ve phase summary güncellendi.
