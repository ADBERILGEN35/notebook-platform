# Search Retention RLS Production Runbook (Faz 107)

Faz 106'da search-service'e eklenen aggregate-only retention dry-run count akışı (`GET /internal/admin/retention/search/plan`) `search_documents` ve `search_reindex_jobs` üzerinde platform-wide aggregate üretir. Search-service veritabanında bugün tenant RLS yoktur; yine de production'da dedicated `SELECT`-only retention role ve preflight önerilir (paylaşılan cluster, gelecekteki RLS, least privilege).

Destructive purge, search document delete, reindex job delete ve index body/snippet export kapsam dışıdır.

## 1. Amaç ve Kapsam

- Kapsam: `search.documents_stale` (archived rows), `search.reindex_jobs_terminal` (terminal jobs).
- Out of scope: `search.documents_active`, `search.indexing_failures_terminal` (inventory-only; ayrı failure tablosu yok).

## 2. Neden Dedicated Role?

Count'lar `content_text`, `search_vector`, `title` okumadan yalnızca `archived_at` / job status + timestamp predicate kullanır. Role yine de yalnızca `SELECT` ile sınırlandırılmalı; yazma yetkisi verilmemelidir.

## 3. Dedicated Retention DB Role

Önerilen rol: `notebook_search_retention`.

```sql
CREATE ROLE notebook_search_retention LOGIN PASSWORD '<replace-with-secret>' BYPASSRLS;
GRANT CONNECT ON DATABASE notebook_platform TO notebook_search_retention;
GRANT USAGE ON SCHEMA public TO notebook_search_retention;
GRANT SELECT ON search_documents, search_reindex_jobs TO notebook_search_retention;
```

Search DB'de RLS bugün yok; `BYPASSRLS` gelecekteki RLS için de güvenli kalır.

## 4. Minimum DB Privilege Set

| Object | Privilege |
|---|---|
| `search_documents` | `SELECT` |
| `search_reindex_jobs` | `SELECT` |

`content_text`, `title`, `tags_text`, `search_vector` kolonları count query'lerinde okunmaz.

## 5. Preflight SQL

Script: [`scripts/retention/check-search-retention-rls-readiness.sql`](../scripts/retention/check-search-retention-rls-readiness.sql)

```bash
psql "$SEARCH_DB_URL" -v retention_role=notebook_search_retention \
  -f scripts/retention/check-search-retention-rls-readiness.sql
```

## 6. Gateway Smoke Script

Script: [`scripts/retention/search-retention-dry-run-smoke.sh`](../scripts/retention/search-retention-dry-run-smoke.sh)

```bash
export API_BASE_URL=https://gateway.example.com
export ADMIN_ACCESS_TOKEN='<admin-jwt>'
export EXPECT_SEARCH_RETENTION_READY=true
bash scripts/retention/search-retention-dry-run-smoke.sh
```

## 7. Env / Toggle Rollout

- search-service: `SEARCH_RETENTION_DRY_RUN_COUNTS_ENABLED`
- gateway: `SEARCH_RETENTION_INTEGRATION_ENABLED`, `SEARCH_RETENTION_INTERNAL_URL`
- Prod default: `false`

## 8. Observability

- Service-local: `search_retention_dry_run_total`, `search_retention_eligible_count`
- Platform: `platform_retention_service_summary_total{service="search-service",...}` (Faz 105)

## 9. Rollback

GitOps `searchRetentionDryRunCountsEnabled` / `searchRetentionIntegrationEnabled` → `"false"`.

## 10. Güvenlik ve PII

Response'ta snippet, body, query text, `content_text`, indexed content yok. Smoke script forbidden token scan uygular (exit 3).

## 11. Production Rollout Checklist

- [ ] `notebook_search_retention` role + grants
- [ ] Preflight SQL OK
- [ ] Service JWT trust configured
- [ ] Smoke exit 0 with `EXPECT_SEARCH_RETENTION_READY=true`
- [ ] Change-request approved

## 12. CI (Faz 108 / Faz 109)

- Fixture: `test-workspace-search-retention-smoke-fixtures.sh` in job `retention-smoke-fixtures`.
- Live staging: `retention-staging-smoke` (opt-in). Workflow: [`.github/workflows/retention-readiness.yml`](../.github/workflows/retention-readiness.yml).
- Faz 109: search domain satırı job summary + sanitized artifact (`retention-staging-smoke-evidence`).
- Optional SQL: `check-search-retention-rls-readiness.sql` (manual `psql`).

### Dedicated retention datasource (Faz 110)

Helm `retentionDatasource.search` → `SEARCH_RETENTION_DATASOURCE_*` (Faz 111 dedicated pool when enabled). Staging E2E: [`staging-dedicated-retention-e2e-checklist.md`](../scripts/retention/staging-dedicated-retention-e2e-checklist.md). **Faz 113:** actuator `searchRetentionDataSourceHealth`. **Faz 114:** staging E2E evidence formats — [`retention-staging-e2e-evidence-formats.md`](../scripts/retention/retention-staging-e2e-evidence-formats.md). See [`retention-datasource-ops-handoff.md`](retention-datasource-ops-handoff.md).

## 13. Related Docs

- [`platform-retention-governance.md`](platform-retention-governance.md) (Faz 106)
- [`retention-rls-production-runbook.md`](retention-rls-production-runbook.md) (content pattern)
