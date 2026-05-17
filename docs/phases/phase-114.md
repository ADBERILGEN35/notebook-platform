# Faz 114: Staging Dedicated Retention Datasource E2E Evidence Runbook Closure

## Hedef

Staging dedicated retention datasource rollout için **evidence ve kabul kriterlerini** netleştirmek: change request’e hangi sanitized özetlerin ekleneceği, hangi artifact’ların toplanacağı, rollout’un ne zaman “green” sayılacağı — gerçek cluster/secret olmadan da uygulanabilir runbook.

## Kapsam (runbook / evidence only)

| Deliverable | Path |
|-------------|------|
| E2E checklist (evidence odaklı) | `scripts/retention/staging-dedicated-retention-e2e-checklist.md` |
| Change request evidence checklist | `scripts/retention/staging-retention-change-request-evidence-checklist.md` |
| Evidence format referansı | `scripts/retention/retention-staging-e2e-evidence-formats.md` |
| Template generator (no secrets) | `scripts/retention/generate-retention-e2e-evidence-template.sh` |
| Ops / governance / readiness / runbooks / CI doc notları | `docs/*`, `.github/workflows/retention-readiness.yml` |

## “Green” staging rollout (özet)

Tümü sağlanmalı (detay checklist’te):

1. GitOps overlay merge + Argo sync OK; ExternalSecret synced.
2. Preflight SQL: content, notification, workspace, search → **pass** (yalnızca özet ticket’a).
3. Actuator: dört servis `lastCheckStatus=UP`, dedicated kullanımı (özet tablo).
4. Smoke: dört domain `passed`, overall exit `0`, artifact validate OK.
5. Metrics: readiness `READY` veya dokümante edilmiş warning; sürekli `UNAVAILABLE` yok.
6. Rollback drill tamamlandı (özet); post-rollback `DISABLED` + smoke `expected-gap` veya disabled state.

## Uygulanmadı

- Backend / frontend production kodu
- Live staging cluster doğrulama (ortam/secret yok)
- Production `retentionDatasource.enabled: true`
- Ham actuator JSON, SQL çıktısı, API body ticket/artifact içinde

Detay: [`phase-114-summary.md`](phase-114-summary.md).
