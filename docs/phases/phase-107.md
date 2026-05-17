# Faz 107: Workspace/Search Retention RLS Preflight + Smoke Scripts

## Hedef

Faz 106 workspace/search dry-run count katmanının production'da güvenle açılabilmesi için operasyon paketi: read-only SQL preflight, gateway smoke script'leri, runbook'lar. Production kodu değişmez.

## Scope içi

- `check-workspace-retention-rls-readiness.sql`, `check-search-retention-rls-readiness.sql`
- `workspace-retention-dry-run-smoke.sh`, `search-retention-dry-run-smoke.sh`
- `test-workspace-search-retention-smoke-fixtures.sh`
- Runbook + governance + production-readiness güncellemeleri

## Scope dışı

- Destructive purge/delete, scheduler, Flyway, microservice, prod default toggle değişikliği, secret hardcode.

## Smoke exit codes

`0` ok / expected gap; `2` readiness gap (expect true); `3` privacy; `4` shape.

Detay: [`phase-107-summary.md`](phase-107-summary.md).
