# Faz 107 Özeti: Workspace/Search Retention RLS Preflight + Smoke Scripts

Önceki faz: [phase-106-summary.md](phase-106-summary.md). Faz spec: [phase-107.md](phase-107.md). Runbook'lar: [`workspace-retention-rls-production-runbook.md`](../workspace-retention-rls-production-runbook.md), [`search-retention-rls-production-runbook.md`](../search-retention-rls-production-runbook.md).

## 1. Yapılanlar

Faz 106 workspace/search dry-run count katmanının production enable öncesi operasyon paketi eklendi: read-only SQL preflight (DB role, RLS, index, bounded probe), gateway platform plan smoke script'leri (privacy + shape guardrail), dedicated runbook'lar ve governance/production-readiness güncellemeleri.

**Production kodu değişmedi.** Helm/GitOps prod default `false` korundu. Script'ler secret içermez; smoke `RETENTION_SMOKE_FIXTURE_FILE` ile fixture test destekler (live gateway gerekmez).

## 2. Backend değişiklikleri

Yok. Faz 106 `WorkspaceRetentionPlanService` / `SearchRetentionPlanService` davranışı (aggregate-only, `WORKSPACE_RETENTION_DB_PERMISSION_DENIED` / `SEARCH_RETENTION_DB_PERMISSION_DENIED`) değişmedi.

## 3. Frontend değişiklikleri

Yok.

## 4. Security/Privacy

- SQL script'ler yalnızca `SELECT` / `SHOW`; DDL/DML yok. Probe'lar `LIMIT 1` bounded; invitation email, workspace name, search title/body/tsvector okunmaz.
- Smoke script'ler response raw text'te forbidden token arar (workspace: `workspaceName`, `userEmail`, `invitationEmail`, …; search: `snippet`, `contentText`, `documentText`, …). İhlalde exit `3` (parse/shape hatasından önce).
- `WORKSPACE_RETENTION_RLS_NOT_READY` / `SEARCH_RETENTION_RLS_NOT_READY` reserved symbolic (Faz 101 paterni); smoke expect=true iken readiness gap sayar.

## 5. Tests

| Komut | Sonuç |
|---|---|
| `bash -n scripts/retention/workspace-retention-dry-run-smoke.sh` | `BASH_SYNTAX_OK` |
| `bash -n scripts/retention/search-retention-dry-run-smoke.sh` | `BASH_SYNTAX_OK` |
| `bash -n scripts/retention/test-workspace-search-retention-smoke-fixtures.sh` | `BASH_SYNTAX_OK` |
| SQL destructive scan (`check-workspace-*`, `check-search-*`) | Yalnızca `has_table_privilege(..., 'DELETE')` metadata sorguları; gerçek `DELETE`/`UPDATE`/`INSERT`/`TRUNCATE`/`DROP`/`ALTER` statement yok |
| `bash scripts/check-no-secrets.sh` | `No obvious committed secrets detected.` |
| `bash scripts/retention/test-workspace-search-retention-smoke-fixtures.sh` | 14 senaryo geçti: ready+expect→0; disabled/unavailable+!expect→0; disabled/unavailable+expect→2; privacy→3; badshape→4 (workspace + search) |

**Çalıştırılmadı (ortam):** Live gateway smoke (`API_BASE_URL` + `ADMIN_ACCESS_TOKEN`); `psql` preflight against staging/prod DB. Prod enable öncesi runbook checklist'te zorunlu.

**Gradle:** Yeni/değişen Java yok — retention unit testleri koşulmadı.

## 6. Config/Deployment

Yeni env/Helm değişikliği yok. Mevcut Faz 106 toggle'lar dokümante edildi:

- workspace: `WORKSPACE_RETENTION_DRY_RUN_COUNTS_ENABLED`, `WORKSPACE_RETENTION_INTEGRATION_ENABLED`
- search: `SEARCH_RETENTION_DRY_RUN_COUNTS_ENABLED`, `SEARCH_RETENTION_INTEGRATION_ENABLED`
- Prod default `false`; dev GitOps'ta `true` (Faz 106'dan devam).

## 7. Eklenen/düzenlenen dosyalar

- **scripts (yeni):** `check-workspace-retention-rls-readiness.sql`, `check-search-retention-rls-readiness.sql`, `workspace-retention-dry-run-smoke.sh`, `search-retention-dry-run-smoke.sh`, `test-workspace-search-retention-smoke-fixtures.sh`
- **docs (yeni):** `workspace-retention-rls-production-runbook.md`, `search-retention-rls-production-runbook.md`, `phases/phase-107.md`, `phases/phase-107-summary.md`
- **docs (düzenlenen):** `platform-retention-governance.md` (Faz 107), `production-readiness.md` (Faz 106–107 checks), `retention-rls-production-runbook.md` (cross-links)

## 8. Kalan açıklar

- Live staging/prod smoke ve `psql` preflight bu fazda koşulmadı (erişim yok).
- Dedicated retention datasource binding Faz 106 ile aynı: DBA/platform engineering datasource override ile çözülür.
- CI'da otomatik gated job (Faz 106 önerisi #2) hâlâ yok; fixture script local/CI için hazır.
- `search.indexing_failures_terminal` / `workspace.membership_inactive` inventory-only (Faz 106'dan devam).

## 9. Sonraki faz önerileri

1. Retention preflight + smoke'u CI staging job'ında otomatik koşturma (workspace + search + content + notification).
2. Dedicated retention datasource Helm chart örneği (opsiyonel, dokümantasyon-only veya values template).
3. Platform retention UI'da service summary drill-down (opsiyonel UX; data zaten `serviceSummaries`'de).
