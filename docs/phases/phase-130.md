# Faz 130: Backend Staging PP Evidence Execution + Bundle GO Preparation

## Hedef

PP-1 (SCIM delta sandbox), PP-2 (break-glass revocation drill) ve opsiyonel PP-3 (retention dedicated datasource E2E) için staging’de tek operasyon sırası, artifact toplama, local/CI bundle build ve CR attach listesini netleştirmek. **Production flag açma yok.**

Önceki faz: [phase-129-summary.md](phase-129-summary.md).

## Scope

| # | Deliverable | Durum |
|---|-------------|--------|
| 1 | `docs/phases/phase-130.md` | Bu dosya |
| 2 | `docs/backend-staging-pp-evidence-execution-plan.md` | Operator runbook |
| 3 | PP sırası + workflow_dispatch talimatları | Runbook içinde |
| 4 | Bundle build (GitHub + local) | Runbook içinde |
| 5 | CR attach listesi (RC + Docker CI dahil) | Runbook + approval gate |
| 6 | `scripts/security/prepare-backend-preprod-evidence-inputs.sh` | Artifact normalizer |
| 7 | `scripts/security/ci-prepare-backend-preprod-evidence-inputs.sh` | CI + `ci.yml` |
| 8 | Doc güncellemeleri | approval gate, readiness review, production-readiness |
| 9 | `docs/phases/phase-130-summary.md` | Faz sonu raporu |

## Kesin kurallar

- Production flag / scheduler / provider mutation / break-glass prod enable / retention prod enable yok.
- Secret/token/JWT/credential dokümana veya artifact şablonuna yazılmaz.
- Eksik evidence → `NO_GO`; fixture-only bundle production GO sayılmaz.
- Live staging bu fazda çalıştırılamadıysa **not run** olarak raporlanır.

## Çıktılar

| Artifact | Path |
|----------|------|
| Execution plan | [backend-staging-pp-evidence-execution-plan.md](../backend-staging-pp-evidence-execution-plan.md) |
| Input preparer | `scripts/security/prepare-backend-preprod-evidence-inputs.sh` |
| Faz özeti | [phase-130-summary.md](phase-130-summary.md) |

## Sonraki adım (operasyon)

1. Staging secrets yapılandır.
2. Runbook sırasıyla PP-1 → PP-2 → (PP-3 gerekirse) → bundle.
3. `finalRecommendation: GO` ile CR aç; Faz 126 flag flip dalgalarını ayrı CR’lerle uygula.
