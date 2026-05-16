# Platform Retention Governance (Faz 98)

Faz 98 platform-wide retention/legal-hold foundation ekler. Production destructive deletion rollout yoktur.

## Flags

| Component | Flag | Default |
|-----------|------|---------|
| identity-service / gateway | `PLATFORM_RETENTION_GOVERNANCE_ENABLED` | `false` |
| identity-service / gateway | `PLATFORM_LEGAL_HOLD_ENABLED` | `false` |
| frontend | `FRONTEND_PLATFORM_RETENTION_GOVERNANCE_ENABLED` | `false` |

## APIs

Gateway:

- `GET /admin/retention/platform/targets`
- `GET /admin/retention/platform/plan`
- `GET /admin/retention/platform/legal-holds`
- `POST /admin/retention/platform/legal-holds`
- `POST /admin/retention/platform/legal-holds/{id}/release`

Identity internal:

- `GET /internal/admin/retention/platform/targets`
- `GET /internal/admin/retention/platform/plan`
- `GET /internal/admin/retention/platform/legal-holds`
- `POST /internal/admin/retention/platform/legal-holds`
- `POST /internal/admin/retention/platform/legal-holds/{id}/release`

## Permissions

- Read: `admin:retention:read`
- Legal hold create/release: `admin:retention:legal-hold:write` plus admin-write MFA when enforced
- Internal service JWT scopes:
  - `internal:admin:retention:platform:read`
  - `internal:admin:retention:legal-hold:write`

## Dry-run planner

The platform plan returns registry targets and hold blocking state. Faz 98 does not perform expensive cross-service row counts; unknown counts are returned as `null` / "Not counted" in the UI.

Destructive purge is not implemented. `dryRun=false` is ignored by the platform planner and returns a warning.

## Audit events

- `PLATFORM_RETENTION_TARGETS_VIEWED`
- `PLATFORM_RETENTION_PLAN_GENERATED`
- `PLATFORM_LEGAL_HOLD_CREATED`
- `PLATFORM_LEGAL_HOLD_RELEASED`
- `PLATFORM_LEGAL_HOLD_CREATE_DENIED`
- `PLATFORM_LEGAL_HOLD_RELEASE_DENIED`

## Metrics

- `platform_retention_plan_generated_total`
- `platform_legal_holds_active{scope}`
- `platform_retention_targets_total{status,riskLevel}`
- `platform_retention_blocked_by_hold_total{targetKey}`

Cardinality is bounded by registry target keys and legal-hold scopes. No workspaceId, userId, email, note id, or raw content labels.

## Faz 99 Content-service Integration

Faz 99 content-service için aggregate-only, legal-hold aware dry-run count desteği ekler. Destructive purge yine yoktur.

### Content-service Internal API

- `GET /internal/admin/retention/content/plan`
  - Auth: Service JWT, scope `internal:admin:retention:read`
  - Query: `dryRun=true` (zorunlu), `target` (opsiyonel), `legalHoldScopes=<ALL_PLATFORM,CONTENT,WORKSPACE,NOTE,USER>` (opsiyonel), `generatedAt` (opsiyonel)
  - Response: `{ service, dryRun, generatedAt, targets[], warnings[] }` aggregate-only; note/comment/email body alanları içermez.

### Targets

Content-service tarafında raporlanan target'lar:

| Target key | Status | Default retention |
|------------|--------|--------------------|
| `content.note_versions` | `DRY_RUN_READY` | 365 gün |
| `content.comments` | `DRY_RUN_READY` | 365 gün |
| `content.search_documents` | `DRY_RUN_READY` | 90 gün |
| `content.notes` | `INVENTORY_ONLY` | — |

Workspaces/notebooks/attachments content-service kapsamı dışındadır.

### Legal-hold mapping

- `ALL_PLATFORM`, `CONTENT` → tüm content target'larını bloklar (purgeableCount=0, blockedByLegalHold=true).
- `WORKSPACE`, `NOTE`, `USER` → warning-only (`CONTENT_RETENTION_PARTIAL_LEGAL_HOLD_MAPPING`).

### Gateway entegrasyonu

Gateway platform planner şu adımları uygular:

1. Identity-service platform planını çeker.
2. `GET /internal/admin/retention/platform/legal-holds?status=ACTIVE` ile aktif hold scope listesini alır.
3. `GatewayContentRetentionProperties.enabled` ise content-service plan endpoint'ini çağırır ve hold scope listesini parametre olarak geçer.
4. Content target satırlarını platform planına merge eder (eligibleCount, purgeableCount, blockedByLegalHold, status, warnings).
5. Content unavailable ise `CONTENT_RETENTION_SERVICE_UNAVAILABLE` plan warning'i ekler; identity planı dönmeye devam eder.

### Content-service config

| Env | Default |
|-----|---------|
| `CONTENT_RETENTION_DRY_RUN_ENABLED` | `false` |
| `CONTENT_RETENTION_MAX_COUNT_QUERY_LIMIT` | `100000` |
| `CONTENT_RETENTION_NOTE_VERSION_RETENTION_DAYS` | `365` |
| `CONTENT_RETENTION_COMMENT_RETENTION_DAYS` | `365` |
| `CONTENT_RETENTION_SEARCH_DOCUMENT_RETENTION_DAYS` | `90` |
| `CONTENT_RETENTION_ADMIN_SERVICE_JWT_KID` / `_PUBLIC_KEY` / `_PUBLIC_KEY_PATH` | empty (configure for prod) |

Gateway config: `CONTENT_RETENTION_INTEGRATION_ENABLED`, `CONTENT_RETENTION_INTERNAL_URL`, `CONTENT_RETENTION_INTERNAL_TIMEOUT_MS`.

### Yeni audit events / metrics

Content-service:

- `CONTENT_RETENTION_DRY_RUN_PLAN_GENERATED`
- `CONTENT_RETENTION_DRY_RUN_FAILED`
- `CONTENT_RETENTION_COUNT_CAPPED`
- Metrics: `content_retention_dry_run_total{target,result}`, `content_retention_eligible_count{target}`, `content_retention_count_duration_seconds{target}`, `content_retention_count_capped_total{target}`

Cardinality bounded `target` registry key ile; workspaceId/noteId/userId/email asla label değil.

### Performance guardrails

- Count query'leri `note_versions.created_at`, `comments.created_at`, `search_index_outbox.created_at` index'leri üzerinden çalışır (V14 migration).
- Her count `LIMIT cap+1` ile sınırlandırılır; cap aşılırsa `CONTENT_RETENTION_QUERY_CAPPED` warning ve audit event üretilir.
- Note body / contentBlocks / comment body asla scan edilmez.

### RLS notu

Retention count endpoint'i cross-workspace aggregate gerektirir. Production'da retention queries için DBA tarafından configure edilen RLS bypass (BYPASSRLS role, migration owner, veya `row_security=off` ile çalışan ayrı admin connection) gerekir. Dev/Testcontainers ortamında `APP_RLS_ENABLED=false` default'u nedeniyle queries doğrudan çalışır.

## Faz 100 Registry Sync ve GitOps Rollout

Faz 100 identity-service platform retention target registry'sini content-service capability'siyle hizalar ve dev/staging ortamlarında content retention integration'ı GitOps üzerinden açar.

### Registry status'ları (identity-service)

| Target key | Status | Service | Risk |
|------------|--------|---------|------|
| `content.note_versions` | `DRY_RUN_READY` | content-service | HIGH |
| `content.comments` | `DRY_RUN_READY` | content-service | HIGH |
| `content.search_documents` | `DRY_RUN_READY` | content-service | MEDIUM |
| `content.workspaces` | `INVENTORY_ONLY` | workspace-service | CRITICAL |
| `content.notebooks` | `INVENTORY_ONLY` | workspace-service | HIGH |
| `content.notes` | `INVENTORY_ONLY` | content-service | CRITICAL |
| `content.attachments_media` | `INVENTORY_ONLY` | object-storage | CRITICAL |

`search.documents` (search-service) registry satırı korunur; `content.search_documents` content-side outbox view'i ayrı target'tır. Naming consolidation gelecek faza bırakıldı.

### `/targets` ve `/plan` tutarlılığı

- `/admin/retention/platform/targets` registry'den okur; tüm content target'ları için yukarıdaki status doğrudan döner.
- `/admin/retention/platform/plan` registry status'unu döner, ardından gateway content-service plan response'undan eligibleCount/purgeableCount/blockedByLegalHold/status/warnings alanlarını override eder.
- Content-service unavailable durumunda `/plan` `CONTENT_RETENTION_SERVICE_UNAVAILABLE` warning'i döner; `/targets` etkilenmez.

### GitOps rollout

Dev/staging için `deploy/gitops/environments/{dev,staging}/values.yaml` content retention integration'ı aktif eder:

```yaml
config:
  contentRetentionDryRunEnabled: "true"
  contentRetentionIntegrationEnabled: "true"
```

Prod (`deploy/gitops/environments/prod/values.yaml`) açıkça disable kalır:

```yaml
config:
  contentRetentionDryRunEnabled: "false"
  contentRetentionIntegrationEnabled: "false"
```

Prod'a alma için ayrı governance/change-request approval gerekir.

### Scope dışı (Faz 100)

- Destructive purge endpoint yok.
- Notification-service retention count integration eklenmedi.
- Per-tenant retention policy yok.
- `search.documents` vs `content.search_documents` consolidation gelecek faz.

## Faz 101 RLS Readiness ve Operasyon

Faz 101 retention dry-run akışını production'da güvenli açabilmek için dokümantasyon ve runtime guardrail ekler. Yeni endpoint veya destructive aksiyon yok.

### Runbook ve scriptler

- Runbook: [`retention-rls-production-runbook.md`](retention-rls-production-runbook.md) — 12 bölümlük operasyon dökümanı (role setup, BYPASSRLS vs `row_security=off`, preflight, smoke, rollback, prod checklist).
- Preflight SQL: [`scripts/retention/check-retention-rls-readiness.sql`](../scripts/retention/check-retention-rls-readiness.sql) — read-only inspection script (role capability, table privilege, index, RLS status, bounded probe).
- Smoke: [`scripts/retention/content-retention-dry-run-smoke.sh`](../scripts/retention/content-retention-dry-run-smoke.sh) — gateway plan response shape ve guardrail warning doğrulaması.

### Yeni warning kodları

| Kod | Anlam |
|---|---|
| `CONTENT_RETENTION_DB_PERMISSION_DENIED` | `ContentRetentionPlanService` count query'sinde `PermissionDeniedDataAccessException` veya PostgreSQL SQLState `42501` yakalandı; retention DB role GRANT'leri eksik veya BYPASSRLS değil. Raw SQL mesajı response'a sızdırılmaz. |
| `CONTENT_RETENTION_RLS_NOT_READY` | Reserve: gelecek bir readiness probe için ayrılmış sembol. Backend şu an aktif olarak üretmiyor; runbook ve smoke script bekleniyor. |

Mevcut `CONTENT_RETENTION_COUNT_FAILED` warning'i generic runtime failure için korunur.

### Prod enable önkoşulları

Prod'da `CONTENT_RETENTION_DRY_RUN_ENABLED=true` ve `CONTENT_RETENTION_INTEGRATION_ENABLED=true` yapılmadan önce:

1. Dedicated retention DB role oluşturulmalı (önerilen: `notebook_content_retention`, BYPASSRLS veya `row_security=off`).
2. Preflight SQL beklenen sonuçları döndürmeli.
3. Smoke script production'a karşı exit 0 üretmeli.
4. Observability dashboard (`content_retention_dry_run_total{result}`) ve alert hazır olmalı.
5. Service JWT trust config gateway signing kid ile rotate edilmiş olmalı.
6. Rollback toggle GitOps PR'ı pre-merge hazır olmalı.
7. Governance change-request approve edilmiş olmalı.

Faz 101 backend kodu ayrı retention datasource binding eklemez; production'da retention path için role override DBA + platform engineering tarafından datasource override veya connection routing ile çözülür (runbook bölüm 6 referansı).

## Faz 102 Notification-service Integration

Faz 102 notification-service retention target'larına aggregate-only, legal-hold aware dry-run count desteği ekler. Destructive purge yine yoktur ve Faz 83 retention worker / Faz 84 legal hold davranışı değişmez — bu yeni katman mevcut `/internal/admin/notifications/retention/plan` (Faz 83) endpoint'inden ayrı, yan yana çalışan platform contract endpoint'idir.

### Notification-service Internal API

- `GET /internal/admin/retention/notification/plan?dryRun=true[&target=...&legalHoldScopes=...&generatedAt=...]`
- Service JWT scope: `internal:admin:retention:read`, audience `notification-service`.
- `dryRun=false` → 400 `RETENTION_DRY_RUN_ONLY`. Bilinmeyen target → 400 `RETENTION_TARGET_UNKNOWN`.
- Response shape Faz 99 ile aynı: `service`, `dryRun`, `generatedAt`, `targets[]` (`targetKey`, `status`, `defaultRetentionDays`, `cutoff`, `eligibleCount`, `purgeableCount`, `blockedByLegalHold`, `warnings`), `warnings[]`. Payload/email body/recipient/userId alanları yer almaz.

### Targets

| targetKey | Tablo / predicate | Default gün | Risk |
|---|---|---|---|
| `notification.analytics_hourly` | `notification_delivery_analytics_hourly` (`bucket_start < cutoff`) | 90 | LOW |
| `notification.fanout_outbox_sent` | `notification_fanout_outbox` (`status='SENT' AND sent_at < cutoff`) | 7 | MEDIUM |
| `notification.fanout_outbox_dead` | `notification_fanout_outbox` (`status='DEAD' AND coalesce(dead_at,created_at) < cutoff`) | 90 | HIGH |
| `notification.dead_letter_requeue_requests` | `notification_dead_letter_requeue_requests` (`created_at < cutoff`) | 90 | MEDIUM |
| `notification.digest_items_terminal` | `notification_digest_items` (`status IN ('SENT','CANCELLED')` + cutoff) | 90 | LOW |
| `notification.email_notifications_terminal` | `email_notifications` (`status IN ('SENT','FAILED','CANCELLED','SKIPPED') AND updated_at < cutoff`) | 90 | HIGH |

### Legal-hold mapping

- Tam bloklayan scope'lar: `ALL_PLATFORM`, `NOTIFICATION`, `ALL_NOTIFICATION_RETENTION` → tüm notification target'larında `purgeableCount=0`, `blockedByLegalHold=true`, `NOTIFICATION_RETENTION_LEGAL_HOLD_BLOCKED`.
- Target-spesifik scope'lar: `FANOUT_OUTBOX` → fanout sent+dead; `DEAD_LETTER_REQUEUE_REQUESTS`; `ANALYTICS`; `DIGEST_ITEMS`; `EMAIL_NOTIFICATIONS`. Kısmi mapping durumunda `NOTIFICATION_RETENTION_PARTIAL_LEGAL_HOLD_MAPPING` warning'i eklenir. Hold reason/metadata response'a hiç yansımaz.

### Gateway entegrasyonu

1. `GatewayNotificationRetentionProperties.enabled` ise gateway `/admin/retention/platform/plan` çağrısında identity plan + content merge sonrası notification plan endpoint'ini çağırır, hold scope listesini parametre geçer.
2. Notification target satırlarına `eligibleCount`/`purgeableCount`/`blockedByLegalHold`/`status`/`defaultRetentionDays`/`cutoff` override edilir; warning'ler dedupe-merge edilir; `PLATFORM_RETENTION_NOTIFICATION_PLAN_INCLUDED` eklenir.
3. Notification-service unavailable/disabled → `NOTIFICATION_RETENTION_SERVICE_UNAVAILABLE` warning'i; identity planı bozulmadan döner. `/targets` etkilenmez.

### Notification-service config

`notification.platform-retention.*` (env): `NOTIFICATION_RETENTION_DRY_RUN_COUNTS_ENABLED` (default `false`), `NOTIFICATION_RETENTION_MAX_COUNT_QUERY_LIMIT` (default `100000`), `NOTIFICATION_ANALYTICS_RETENTION_DAYS`, `NOTIFICATION_FANOUT_SENT_RETENTION_DAYS`, `NOTIFICATION_FANOUT_DEAD_RETENTION_DAYS`, `NOTIFICATION_DEAD_LETTER_REQUEUE_REQUEST_RETENTION_DAYS`, `NOTIFICATION_DIGEST_SENT_RETENTION_DAYS`, `NOTIFICATION_EMAIL_TERMINAL_RETENTION_DAYS`.

Gateway: `NOTIFICATION_RETENTION_INTEGRATION_ENABLED` (default `false`), `NOTIFICATION_RETENTION_INTERNAL_URL`, `NOTIFICATION_RETENTION_INTERNAL_TIMEOUT_MS`. GitOps: dev/staging `"true"`, prod açıkça `"false"`.

### Yeni audit events / warning kodları

Notification-service audit (`NOTIFICATION_RETENTION` aggregate): `NOTIFICATION_RETENTION_DRY_RUN_PLAN_GENERATED`, `NOTIFICATION_RETENTION_DRY_RUN_FAILED`, `NOTIFICATION_RETENTION_COUNT_CAPPED`.

| Kod | Anlam |
|---|---|
| `NOTIFICATION_RETENTION_DRY_RUN_DISABLED` | Feature flag kapalı; target listesi boş döner. |
| `NOTIFICATION_RETENTION_LEGAL_HOLD_BLOCKED` | Aktif full legal hold tüm target'ları blokladı. |
| `NOTIFICATION_RETENTION_PARTIAL_LEGAL_HOLD_MAPPING` | Scope yalnız bazı target'lara map edildi. |
| `NOTIFICATION_RETENTION_TARGET_INVENTORY_ONLY` | Target dry-run hazır değil; sadece envanter. |
| `NOTIFICATION_RETENTION_QUERY_CAPPED` | Count `maxCountQueryLimit`'e ulaştı; değer cap'lendi. |
| `NOTIFICATION_RETENTION_COUNT_FAILED` | Generic count runtime hatası. |
| `NOTIFICATION_RETENTION_DB_PERMISSION_DENIED` | `PermissionDeniedDataAccessException` / SQLState `42501`; retention DB role GRANT/BYPASSRLS eksik. Raw SQL sızdırılmaz. |
| `NOTIFICATION_RETENTION_SERVICE_UNAVAILABLE` | Gateway notification plan endpoint'ine ulaşamadı; identity planı korunur. |

### Performance guardrails

Count query'leri bounded cap pattern'i (`SELECT count(*) FROM (SELECT 1 ... WHERE <indexed pred> LIMIT cap+1) bounded`) kullanır; full body/JSON scan yapılmaz. Faz 83 indexleri (`idx_notification_delivery_analytics_bucket`, `idx_fanout_outbox_status_next`, `idx_digest_items_status_scheduled`, `idx_email_status_next_attempt`) yeniden kullanılır — yeni Flyway migration yoktur. Metric'ler: `notification_retention_dry_run_total{target,result}`, `notification_retention_count_duration_seconds{target}`, `notification_retention_eligible_count{target}`, `notification_retention_count_capped_total{target}`; yasak label (`userId`/`email`/`recipient`/`workspaceId`) kullanılmaz.

### RLS notu

Notification tabloları cross-workspace aggregate gerektirir; Faz 101 retention RLS runbook'undaki dedicated retention DB role / `row_security=off` koşulu notification için de geçerlidir. Bu fazda dev/staging default `dryRunCountsEnabled=true`, prod `false`.

## Faz 103 Notification RLS Readiness ve Operasyon

Faz 103 Faz 102 notification retention dry-run akışını production'da güvenle açabilmek için operasyon dokümantasyonu, preflight SQL ve smoke script ekler. Yeni endpoint, contract değişikliği, destructive aksiyon veya Flyway migration yoktur; production kodu değişmez.

### Runbook ve scriptler

- Runbook: [`notification-retention-rls-production-runbook.md`](notification-retention-rls-production-runbook.md) — 12 bölümlük operasyon dökümanı (Faz 83 worker vs Faz 102 platform farkı, dedicated `notebook_notification_retention` role, BYPASSRLS vs `row_security=off`, minimum privilege, preflight, smoke, rollback, prod checklist, security/PII sınırları).
- Preflight SQL: [`scripts/retention/check-notification-retention-rls-readiness.sql`](../scripts/retention/check-notification-retention-rls-readiness.sql) — read-only inspection script (role capability, table privilege, Faz 83 index reuse, RLS status, bounded probe). DDL/DML yok.
- Smoke: [`scripts/retention/notification-retention-dry-run-smoke.sh`](../scripts/retention/notification-retention-dry-run-smoke.sh) — gateway plan response shape, 6 notification target ve aggregate-only/privacy guardrail doğrulaması.

### Smoke exit kodları (Faz 103 şeması)

| Kod | Anlam |
|---|---|
| `0` | Smoke başarılı (veya `EXPECT_NOTIFICATION_RETENTION_READY=false` iken beklenen unavailable/disabled durumu) |
| `2` | `NOTIFICATION_RETENTION_SERVICE_UNAVAILABLE` / `NOTIFICATION_RETENTION_DB_PERMISSION_DENIED` / `NOTIFICATION_RETENTION_RLS_NOT_READY` readiness gap (`EXPECT_NOTIFICATION_RETENTION_READY=true` iken) |
| `3` | Privacy guardrail ihlali (raw payload/body/recipient/email/userId/workspaceId token'ı response'ta) |
| `4` | Schema/shape mismatch (hatalı HTTP, geçersiz JSON, eksik key/tip) |

`NOTIFICATION_RETENTION_RLS_NOT_READY` gelecek bir readiness probe için ayrılmış symbolic reserve'dir; backend aktif üretmez, smoke script readiness gap sinyali olarak yorumlar (Faz 101 `CONTENT_RETENTION_RLS_NOT_READY` paterni).

### Prod enable önkoşulları

Prod'da `NOTIFICATION_RETENTION_DRY_RUN_COUNTS_ENABLED=true` ve `NOTIFICATION_RETENTION_INTEGRATION_ENABLED=true` yapılmadan önce:

1. Dedicated retention DB role oluşturulmalı (önerilen: `notebook_notification_retention`, BYPASSRLS veya `row_security=off`, 5 target tablosunda `SELECT`-only).
2. Preflight SQL beklenen sonuçları döndürmeli.
3. Smoke script production'a karşı `EXPECT_NOTIFICATION_RETENTION_READY=true` ile exit 0 üretmeli.
4. Observability dashboard (`notification_retention_dry_run_total{result}`) ve `result="error"` alert hazır olmalı.
5. Service JWT trust config gateway signing kid ile rotate edilmiş olmalı.
6. Rollback toggle GitOps PR'ı pre-merge hazır olmalı.
7. Governance change-request approve edilmiş olmalı.

Prod default `false` korunur; production enable ayrı GitOps PR ve onay gerektirir.
