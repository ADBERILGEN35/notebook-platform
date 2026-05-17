# Faz 108 Özeti: Retention Preflight + Smoke CI Staging Job

Önceki faz: [phase-107-summary.md](phase-107-summary.md). Faz spec: [phase-108.md](phase-108.md).

## 1. Yapılanlar

Retention dry-run readiness kontrolleri CI'ya taşındı: secret gerektirmeyen fixture/observability gate (`retention-smoke-fixtures`) ve opt-in staging live gateway smoke (`retention-staging-smoke`). Content smoke script Faz 103 exit-code şemasına hizalandı; notification'a fixture desteği eklendi; orchestrator script'ler ve runbook/governance güncellemeleri yapıldı.

**Production kodu, Helm prod default, destructive purge değişmedi.**

### CI workflow job adları

| Job | Workflow |
|-----|----------|
| `retention-smoke-fixtures` | [`.github/workflows/retention-readiness.yml`](../../.github/workflows/retention-readiness.yml) |
| `retention-staging-smoke` | aynı |

- **`retention-smoke-fixtures`**: PR ve `main` push'ta **zorunlu**; secret yokken **skip yok** — her zaman çalışır.
- **`retention-staging-smoke`**: `staging` branch push veya `workflow_dispatch` + `run_staging_smoke=true` iken çalışır; `RETENTION_STAGING_API_BASE_URL` veya `RETENTION_STAGING_ADMIN_ACCESS_TOKEN` yoksa **graceful skip (exit 0)**. PR pipeline'da bu job tetiklenmez.

## 2. Backend değişiklikleri

Yok.

## 3. Frontend değişiklikleri

Yok.

## 4. Security/Privacy

- CI'da secret hardcode yok; staging secret'ları GitHub repository secrets.
- `ADMIN_ACCESS_TOKEN` orchestrator ve smoke script'lerde loglanmaz; HTTP hata durumunda response body stderr'e yazılmaz.
- Staging orchestrator yalnızca sanitize edilmiş satırları (targets seen, Warnings, Readiness, Privacy, …) loglar.
- Aggregated staging failure: exit `3` (privacy) > `4` (shape) > `2` (readiness).
- SQL preflight CI'da koşulmaz (opsiyonel manuel `psql`); script'ler read-only kalır.

## 5. Tests

| Komut | Sonuç |
|---|---|
| `bash scripts/retention/ci-retention-smoke-fixtures.sh` (WSL) | **PASS** — syntax 4 smoke, workspace/search 14 senaryo, content/notification 10 senaryo, observability JSON/YAML + label guardrail, `check-no-secrets` |
| `bash scripts/retention/run-retention-staging-smoke.sh` (secret yok) | **PASS** — graceful skip, exit 0 |
| Live staging gateway smoke | **Çalıştırılmadı** — `RETENTION_STAGING_*` secret'ları ve staging gateway erişimi yok |
| `psql` SQL preflight (4 script) | **Çalıştırılmadı** — CI dışı manuel adım |

Fixture matris özeti: ready+expect→0; unavailable/disabled+!expect→0; unavailable+expect→2; privacy→3; badshape→4 (content, notification, workspace, search).

## 6. Config/Deployment

**Yeni GitHub repository secrets (opsiyonel, staging smoke için):**

| Secret | Maps to env |
|--------|-------------|
| `RETENTION_STAGING_API_BASE_URL` | `API_BASE_URL` |
| `RETENTION_STAGING_ADMIN_ACCESS_TOKEN` | `ADMIN_ACCESS_TOKEN` |

**Opsiyonel repository variables (staging expect override):** `RETENTION_EXPECT_CONTENT_READY`, `RETENTION_EXPECT_NOTIFICATION_READY`, `RETENTION_EXPECT_WORKSPACE_READY`, `RETENTION_EXPECT_SEARCH_READY` (workflow default `true` when unset).

**Smoke env (staging job):** `EXPECT_CONTENT_RETENTION_READY`, `EXPECT_NOTIFICATION_RETENTION_READY`, `EXPECT_WORKSPACE_RETENTION_READY`, `EXPECT_SEARCH_RETENTION_READY`.

Prod Helm/GitOps default'ları değişmedi.

## 7. Eklenen/düzenlenen dosyalar

- **CI:** `.github/workflows/retention-readiness.yml`
- **scripts (yeni):** `ci-retention-smoke-fixtures.sh`, `run-retention-staging-smoke.sh`, `validate-retention-observability.sh`, `test-content-notification-retention-smoke-fixtures.sh`
- **scripts (düzenlenen):** `content-retention-dry-run-smoke.sh` (exit 0/2/3/4, `API_BASE_URL`/`ADMIN_ACCESS_TOKEN`/`EXPECT_CONTENT_RETENTION_READY`, fixture), `notification-retention-dry-run-smoke.sh` (fixture, no body dump on HTTP error), `workspace-retention-dry-run-smoke.sh`, `search-retention-dry-run-smoke.sh` (no body dump on HTTP error)
- **docs (yeni):** `phases/phase-108.md`, `phases/phase-108-summary.md`
- **docs (düzenlenen):** `platform-retention-governance.md`, `production-readiness.md`, `retention-rls-production-runbook.md`, `notification-retention-rls-production-runbook.md`, `workspace-retention-rls-production-runbook.md`, `search-retention-rls-production-runbook.md`

## 8. Kalan açıklar

- Live **staging** smoke bu ortamda koşulmadı; secret'lar tanımlandıktan sonra `workflow_dispatch` veya `staging` push ile doğrulanmalı.
- SQL preflight CI'da otomatik değil; production enable öncesi runbook checklist'te manuel `psql` gerekir.
- `main` push sonrası otomatik staging smoke yok (yalnızca `staging` branch veya manual dispatch) — bilinçli opt-in.

## 9. Sonraki faz önerileri

1. Staging secret'ları tanımlandıktan sonra ilk `retention-staging-smoke` green run ve sonuçların runbook'a işlenmesi.
2. Dedicated retention datasource Helm values template (opsiyonel, dokümantasyon-only).
3. Platform retention UI service summary drill-down (opsiyonel UX).
