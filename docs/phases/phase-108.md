# Faz 108: Retention Preflight + Smoke CI Staging Job

## Hedef

Content, notification, workspace ve search retention dry-run readiness kontrollerini manuel runbook'tan çıkarıp CI'da güvenli, secret'sız (fixture) ve opt-in staging (live gateway) gate'lerine taşımak. Production default değişmez; destructive purge yok.

## Scope içi

- `.github/workflows/retention-readiness.yml` — `retention-smoke-fixtures` + `retention-staging-smoke`
- `scripts/retention/ci-retention-smoke-fixtures.sh`, `run-retention-staging-smoke.sh`, `validate-retention-observability.sh`
- `test-content-notification-retention-smoke-fixtures.sh`
- `content-retention-dry-run-smoke.sh` exit-code/env hizalaması (Faz 103 şeması)
- Governance, production-readiness, retention runbook CI bölümleri

## Scope dışı

- Destructive purge/delete, scheduler, Flyway, yeni microservice
- Production default toggle değişikliği
- CI içinde secret hardcode
- CI'da zorunlu live DB `psql` preflight (yalnızca dokümante opsiyonel adım)

## CI jobs

| Job | Zorunlu? | Secret |
|-----|----------|--------|
| `retention-smoke-fixtures` | Evet (PR + `main` push) | Hayır |
| `retention-staging-smoke` | Hayır (opt-in) | `RETENTION_STAGING_API_BASE_URL`, `RETENTION_STAGING_ADMIN_ACCESS_TOKEN` yoksa graceful skip (exit 0) |

**Staging tetikleyiciler:** `push` → `staging` branch; `workflow_dispatch` + `run_staging_smoke=true`. Production URL default yok.

## Smoke env (staging)

| Değişken | Açıklama |
|----------|----------|
| `API_BASE_URL` | Gateway base (secret `RETENTION_STAGING_API_BASE_URL`) |
| `ADMIN_ACCESS_TOKEN` | Admin JWT; loglanmaz |
| `EXPECT_CONTENT_RETENTION_READY` | `true` staging job default (repo variable ile override) |
| `EXPECT_NOTIFICATION_RETENTION_READY` | aynı |
| `EXPECT_WORKSPACE_RETENTION_READY` | aynı |
| `EXPECT_SEARCH_RETENTION_READY` | aynı |

## Opsiyonel SQL preflight (CI dışı / manuel)

`psql` ile migration owner olarak (DB URL secret gerekir; CI'da zorunlu değil):

- `scripts/retention/check-retention-rls-readiness.sql` (content)
- `scripts/retention/check-notification-retention-rls-readiness.sql`
- `scripts/retention/check-workspace-retention-rls-readiness.sql`
- `scripts/retention/check-search-retention-rls-readiness.sql`

## Smoke exit codes (tüm domainler)

| Kod | Anlam |
|-----|--------|
| `0` | OK veya beklenen pre-rollout gap |
| `2` | Readiness gap (`EXPECT_*=true`) |
| `3` | Privacy violation (en yüksek öncelik) |
| `4` | Shape/contract mismatch |

Detay: [`phase-108-summary.md`](phase-108-summary.md).
