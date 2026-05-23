# Faz 152: Staging Deployment + Smoke Runbook & GitOps Bootstrap

## 1. Yapılanlar

Staging RC deploy ve smoke akışı GitOps/Helm/runbook seviyesinde dokümante edildi. Örnek RC overlay eklendi. **Kod değişikliği yok.** **Production flag açılmadı.**

Önceki: [phase-151-summary.md](phase-151-summary.md).

## 2. Deliverables

| Artifact | Path |
|----------|------|
| Deployment + smoke runbook | [staging-deployment-smoke-runbook.md](../staging-deployment-smoke-runbook.md) |
| RC deploy overlay (example) | [staging-rc-deploy.overlay.example.yaml](../../deploy/gitops/environments/staging/staging-rc-deploy.overlay.example.yaml) |

## 3. Kapsam

- RC image tag / GitOps promote / Argo sync
- Helm template + kubectl rollout smoke commands
- Staging smoke checklist (gate before PP-1/PP-2)
- GitHub Actions sırası
- Rollback runbook
- Cross-links to bootstrap + PP checklists

## 4. Karar durumu (değişmez)

| Alan | Karar |
|------|--------|
| Frontend | **GO_WITH_ACCEPTED_RISKS** |
| Backend | **NO_GO** |
| Platform | **NO_GO** |

## 5. Sonraki

Operatör runbook §2–§3 uygula → bootstrap step 12+ → PP-1/PP-2 → bundle GO → sign-off güncelle.
