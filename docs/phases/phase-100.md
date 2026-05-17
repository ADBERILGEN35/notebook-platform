# Faz 100: Platform Retention Registry Sync + GitOps Rollout

## Hedef

Faz 99'da content-service için eklenen retention dry-run count desteğini platform retention registry ve deployment konfigürasyonu ile hizalamak. Amaç, identity-service target inventory endpoint'i ile gateway plan response'unun content target status'ları açısından tutarlı hale gelmesi ve dev/staging ortamları için kontrollü GitOps rollout hazırlığı yapılmasıdır.

Bu fazda destructive purge eklenmeyecek. Sadece registry status sync, config rollout, documentation ve ops görünürlüğü iyileştirilecek.

## Mevcut Durum

Faz 99'da content-service aggregate-only retention dry-run endpoint'i eklendi:

- `GET /internal/admin/retention/content/plan`
- `content.note_versions`, `content.comments`, `content.search_documents` için `DRY_RUN_READY` count visibility var.
- Gateway platform planner content-service planını identity plan response'una merge ediyor.
- Frontend platform retention ekranı content count, blocked, capped ve unavailable state'lerini gösterebiliyor.
- Destructive purge endpoint'i yok.

Kalan açık: identity-service `RetentionTargetRegistry` hâlâ bazı content target'ları `INVENTORY_ONLY` döndürüyor. Gateway plan response content-service'ten gelen status ile override ediyor; ancak `/targets` inventory endpoint'i ile `/plan` response'u arasında status tutarsızlığı oluşabiliyor.

## Scope İçi

- Identity-service platform retention target registry'de content target status'larını Faz 99 ile hizalamak.
- `content.note_versions`, `content.comments`, `content.search_documents` target'larını `DRY_RUN_READY` yapmak.
- `content.workspaces`, `content.notebooks`, `content.notes`, `content.attachments` target'larını `INVENTORY_ONLY` olarak korumak.
- Gateway/platform retention target inventory response'unun content-service capability bilgisiyle uyumlu olmasını sağlamak.
- Dev/staging için GitOps values override hazırlamak:
  - `CONTENT_RETENTION_DRY_RUN_ENABLED=true`
  - `CONTENT_RETENTION_INTEGRATION_ENABLED=true`
  - prod default kapalı kalmalı.
- Platform retention UI'da target inventory ve plan status tutarlılığını korumak.
- Docs ve tests güncellemek.

## Scope Dışı

- Actual content purge.
- Destructive purge endpoint'i.
- Archive-before-delete.
- RLS production bypass implementation.
- Object storage retention.
- eDiscovery export.
- Notification-service retention count implementation.
- Per-tenant retention policy.
- Yeni microservice.

## Registry Sync

Identity-service registry'de content target status'ları:

### DRY_RUN_READY

- `content.note_versions`
- `content.comments`
- `content.search_documents`

### INVENTORY_ONLY

- `content.workspaces`
- `content.notebooks`
- `content.notes`
- `content.attachments`

Registry metadata'da mümkünse şu bilgiler hizalanmalı:

- `dryRunSupported=true` for DRY_RUN_READY targets
- `destructivePurgeSupported=false` for all content targets
- `legalHoldSupported=true`
- `riskLevel=HIGH` for note/workspace/notebook
- `riskLevel=MEDIUM` or `LOW` for derived/search targets, mevcut enum modeline göre

## Gateway / Planner Davranışı

Gateway plan merge davranışı korunur:

- `/targets` inventory response registry'den tutarlı status döndürmeli.
- `/plan` response content-service enriched count değerleriyle dönmeli.
- Content-service unavailable ise `/plan` partial warning döner.
- `/targets` endpoint content-service unavailable olsa bile registry status döndürmeye devam edebilir.

Bu fazda gateway'in content-service health/live validation yapması zorunlu değildir.

## GitOps Rollout

Dev/staging GitOps values için önerilen override:

```yaml
config:
  contentRetentionDryRunEnabled: "true"
  contentRetentionIntegrationEnabled: "true"
```

Prod:

```yaml
config:
  contentRetentionDryRunEnabled: "false"
  contentRetentionIntegrationEnabled: "false"
```

Prod'da enable etmek için ayrı onay/GitOps PR gerekir.

## Config

Mevcut Faz 99 config değerleri korunur:

- `CONTENT_RETENTION_DRY_RUN_ENABLED`
- `CONTENT_RETENTION_INTEGRATION_ENABLED`
- `CONTENT_RETENTION_INTERNAL_URL`
- `CONTENT_RETENTION_INTERNAL_TIMEOUT_MS`
- `CONTENT_RETENTION_MAX_COUNT_QUERY_LIMIT`
- `CONTENT_RETENTION_NOTE_VERSION_RETENTION_DAYS`
- `CONTENT_RETENTION_COMMENT_RETENTION_DAYS`
- `CONTENT_RETENTION_SEARCH_DOCUMENT_RETENTION_DAYS`

Yeni config zorunlu değil.

## Security / Privacy

- Destructive purge yok.
- `/targets` ve `/plan` response raw content içermez.
- Note title, note body, contentBlocks, comment body, user email dönülmez.
- Admin permission gate korunur.
- Service JWT trust modeli değişmez.
- Prod config conservative kalır.
- Metrics label'larında high-cardinality identifier kullanılmaz.

## Test Gereksinimleri

### Identity-service

- Retention target registry `content.note_versions` için `DRY_RUN_READY` döner.
- Retention target registry `content.comments` için `DRY_RUN_READY` döner.
- Retention target registry `content.search_documents` için `DRY_RUN_READY` döner.
- Workspace/notebook/note/attachment target'ları `INVENTORY_ONLY` kalır.
- Registry target response raw content içermez.
- Existing platform retention tests güncellenir.

### Api-gateway

- `/admin/retention/platform/targets` content target status'larını güncel döndürür.
- `/admin/retention/platform/plan` content-service enriched status/count değerlerini korur.
- Content-service unavailable durumunda `/targets` etkilenmez, `/plan` warning döner.
- Missing permission denied testleri korunur.

### Frontend

- Target inventory veya plan UI'da `DRY_RUN_READY` content targets doğru badge ile görünür.
- `INVENTORY_ONLY` content targets doğru badge ile görünür.
- Plan ve inventory status çelişkisi testlerde engellenir.
- No purge action visible.

### Deploy

- GitOps dev/staging values content retention integration için enable edilir.
- Prod defaults disabled kalır.
- Helm template valid olmalı.

## Kabul Kriterleri

- Identity-service retention target registry content target status'ları Faz 99 ile uyumlu.
- `/targets` ve `/plan` arasında content target status tutarlılığı sağlandı.
- Dev/staging GitOps override content retention dry-run integration için hazır.
- Prod default kapalı.
- Destructive purge eklenmedi.
- Backend/gateway/frontend/deploy testleri güncellendi.
- Docs ve phase summary güncellendi.
