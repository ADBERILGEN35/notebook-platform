# Faz 106 Özeti: Workspace/Search Retention Dry-run Counts

Önceki faz: [phase-105-summary.md](phase-105-summary.md). Faz spec: [phase-106.md](phase-106.md). Governance: [`platform-retention-governance.md`](../platform-retention-governance.md) → "Faz 106 Workspace/Search Retention Dry-run Counts".

## 1. Yapılanlar

Platform retention planındaki son `INVENTORY_ONLY` domain'ler için workspace-service ve search-service'e Faz 99/102 ile aynı aggregate-only dry-run count katmanı eklendi. Gateway platform planner search ve workspace internal plan'larını sırayla merge eder; Faz 104 `serviceSummaries` ve Faz 105 metrics publisher ek publisher olmadan `search-service` / `workspace-service` label'larını kapsar.

Destructive purge, delete, scheduler veya tenant-specific policy yok. Implementasyon sırası: önce search (düşük risk, türev veri), sonra workspace (invitation + audit count; membership inactive ve core entity'ler inventory-only).

## 2. Backend değişiklikleri

**search-service** (`admin/retention/`):

- `GET /internal/admin/retention/search/plan` — Service JWT `internal:admin:retention:read`.
- `DRY_RUN_READY`: `search.documents_stale` (`archived_at < cutoff`), `search.reindex_jobs_terminal` (terminal status + zaman < cutoff).
- `INVENTORY_ONLY`: `search.documents_active`, `search.indexing_failures_terminal` (ayrı failure tablosu yok).
- JdbcTemplate capped count; audit + `search_retention_*` Micrometer.

**workspace-service** (`admin/retention/`):

- `GET /internal/admin/retention/workspace/plan`.
- `DRY_RUN_READY`: `workspace.invitations_expired` (expired pending), `workspace.audit_like_events` (`workspace_audit_events.created_at`).
- `INVENTORY_ONLY`: memberships inactive (kolon yok), workspaces/notebooks/tags/memberships registry satırları.
- `workspace_retention_*` Micrometer.

**identity-service**:

- `RetentionTargetRegistry`: 11 yeni `workspace.*` / `search.*` satırı; mevcut `search.documents` umbrella `INVENTORY_ONLY` kalır.

**api-gateway**:

- `SearchRetentionClient`, `WorkspaceRetentionClient`; `mergeSearch` → `mergeWorkspace` zinciri.
- `serviceFromKey` / `serviceForWarning` + `PlatformRetentionMetricsPublisher.KNOWN_SERVICES` genişletmesi (yeni publisher yok).

## 3. Frontend değişiklikleri

Yok (spec: data-driven mevcut platform retention UI target/status/count alanlarını registry + merge'den alır).

## 4. Security/Privacy

- Response aggregate-only: workspace adı, notebook title, user email, search body/snippet/query, legal hold reason yok.
- Count query'leri indexed timestamp/status; `LIMIT cap+1`; FTS/body scan yok.
- Cross-workspace aggregate → production'da retention DB role / BYPASSRLS (Faz 101 runbook ile aynı model).
- Service JWT zorunlu; gateway admin permission gate değişmedi.

## 5. Tests

| Komut | Sonuç |
|---|---|
| `./gradlew :search-service:test --tests "*SearchRetention*"` | BUILD SUCCESSFUL |
| `./gradlew :workspace-service:test --tests "*WorkspaceRetention*"` | BUILD SUCCESSFUL |
| `./gradlew :api-gateway:test --tests "*AdminPlatformRetention*" --tests "*PlatformRetentionMetrics*"` | BUILD SUCCESSFUL |
| `./gradlew :identity-service:test --tests "*PlatformRetentionGovernance*"` | BUILD SUCCESSFUL |

Yeni: `SearchRetentionPlanServiceTest`, `WorkspaceRetentionPlanServiceTest`; gateway `mergeSearchPlan` + `serviceFromKey` testleri.

## 6. Config/Deployment

Helm `values.yaml` + `configmap.yaml`: search/workspace retention env'leri (default `false`). GitOps `dev/values.yaml`: dry-run + integration `true` (search + workspace). Prod kapalı kalır.

## 7. Eklenen/düzenlenen dosyalar

- **search-service**: `admin/retention/*` (10 Java), test, `application.yml`, `SearchServiceApplication.java`.
- **workspace-service**: `admin/retention/*` (10 Java), test, `application.yml`, `WorkspaceServiceApplication.java`.
- **identity-service**: `RetentionTargetRegistry.java`.
- **api-gateway**: clients, properties, `AdminPlatformRetentionProxyService`, `PlatformRetentionMetricsPublisher`, tests, `application.yml`, `ApiGatewayApplication.java`.
- **deploy**: `helm/.../values.yaml`, `configmap.yaml`, `gitops/environments/dev/values.yaml`.
- **docs**: `platform-retention-governance.md`, `docs/phases/phase-106-summary.md`.

## 8. Kalan açıklar

- `search.indexing_failures_terminal` ve `workspace.membership_inactive` şema karşılığı olmadığı için inventory-only; docs/test ile netleştirildi.
- `search.documents` (umbrella) ile `search.documents_stale` naming consolidation gelecek fazda değerlendirilebilir.
- Workspace/search prod enable için ayrı GitOps PR + retention DB role preflight (Faz 101/103 runbook modeli) gerekir.
- Dedicated workspace/search retention smoke script bu fazda eklenmedi (content/notification smoke paterni takip edilebilir).

## 9. Sonraki faz önerileri

1. Workspace/search retention RLS preflight SQL + gateway smoke script (CI gated staging job).
2. `search.documents` registry consolidation ve indexing-failure modeli netleşince `search.indexing_failures_terminal` dry-run.
3. Gauge-tabanlı readiness snapshot + production alert tuning (Faz 105 devamı).
