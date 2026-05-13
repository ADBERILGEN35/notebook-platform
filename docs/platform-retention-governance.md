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
