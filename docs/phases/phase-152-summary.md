# Faz 152 Özeti: Staging Deployment + Smoke Runbook & GitOps Bootstrap

Spec: [phase-152.md](phase-152.md). Önceki: [phase-151-summary.md](phase-151-summary.md).

## 1. Yapılanlar

Staging RC deploy, Helm/kubectl doğrulama, smoke checklist (PP öncesi gate), GitHub Actions sırası ve staging rollback runbook hazırlandı. Örnek GitOps overlay eklendi. Mevcut release dokümanları güncellendi. **Backend API değişikliği yok.** **Frontend product feature yok.** **Production flag açılmadı.**

| Deliverable | Dosya |
|-------------|--------|
| Runbook | [staging-deployment-smoke-runbook.md](../staging-deployment-smoke-runbook.md) |
| RC overlay (example) | [staging-rc-deploy.overlay.example.yaml](../../deploy/gitops/environments/staging/staging-rc-deploy.overlay.example.yaml) |

## 2. Staging deployment akışı (özet)

| Adım | Eylem | Çıkış |
|------|-------|--------|
| 1 | RC CI PASS (backend + frontend) | RC SHA |
| 2 | `release-images.yml` → registry tag | Immutable image |
| 3 | `gitops-promote.yml` veya PR → staging `values.yaml` / overlay | Tag promoted |
| 4 | Argo sync / helm upgrade | Pods ready |
| 5 | `helm template` + `kubectl rollout status` | Render OK |
| 6 | Gateway/identity `curl` health | HTTP 200 |
| 7 | Smoke SM-1–SM-9 | Gate pass |
| 8 | GitHub staging secrets | `check-staging-pp-secrets` present |
| 9 | PP-1 → PP-2 → bundle | Live evidence |

## 3. Smoke checklist (PP öncesi)

| ID | Kontrol |
|----|---------|
| SM-1 | Gateway `/actuator/health` |
| SM-2 | Identity login reachable |
| SM-3 | Admin bootstrap |
| SM-4 | Admin token obtain (GitHub secrets) |
| SM-5 | Admin permission GET |
| SM-6 | Break-glass status / sessions |
| SM-7 | SCIM delta readiness (`certified`) |
| SM-8 | Revoke → 401 `BREAK_GLASS_TOKEN_REVOKED` |
| SM-9 | No secret logs |

**Kural:** Smoke pass olmadan PP-1/PP-2 dispatch etme.

## 4. GitOps / Helm overlay

| Dosya | Rol |
|-------|-----|
| `environments/staging/values.yaml` | Staging defaults (placeholder hosts/tags) |
| `staging-rc-deploy.overlay.example.yaml` | RC tag + JDBC/Redis/ingress placeholders |
| `scim-delta-remote-fetch-enable.overlay.example.yaml` | PP-1 (after ExternalSecret) |
| `externalsecret-scim-delta-keys.example.yaml` | SCIM bearer remoteRef fragment |

## 5. Rollback akışı (özet)

GitOps revert → Argo rollback (opsiyonel) → önceki image tag → rollout restart → gateway health → secret rotation (gerekirse) → smoke yeniden.

## 6. PP-1 / PP-2 geçiş koşulları

| Koşul | Gereksinim |
|-------|------------|
| PP-1 | SM-1–SM-5 + GitHub SCIM secrets |
| PP-2 | SM-1–SM-6 + break-glass drill flags + tokens |
| Bundle GO | PP-1 `certified` + PP-2 `passed` |
| Backend GO | Bundle GO on RC SHA |
| Platform GO | Backend GO + frontend sign-off rules |

## 7. Karar durumu

| Alan | Karar |
|------|--------|
| **Frontend** | **GO_WITH_ACCEPTED_RISKS** (değişmedi) |
| **Backend** | **NO_GO** — staging deploy/smoke/PP live evidence eksik |
| **Platform final decision** | **NO_GO** |
| **Production flags** | **Açılmadı** |

## 8. Test / validation

| Komut | Sonuç |
|-------|--------|
| `bash scripts/check-no-secrets.sh` | **PASS** |
| `bash scripts/helm-template-check.sh` | **PASS** (veya skip if no helm) |
| `check-staging-pp-secrets.sh --print-required` | **PASS** |
| `check-staging-pp-secrets.sh --check-github` | **MISSING** (beklenen) |
| `ci-generate-platform-rc-signoff-template.sh` | **PASS** |
| `ci-backend-preprod-evidence-bundle.sh` | **PASS** (fixture) |

## 9. Dokümantasyon güncellemeleri

- `staging-environment-bootstrap-plan.md`
- `backend-live-staging-pp-evidence-run-checklist.md`
- `platform-release-candidate-signoff.md`
- `platform-release-go-no-go-checklist.md`
- `production-readiness.md`

## 10. Sonraki adım

Operatör [staging-deployment-smoke-runbook.md](../staging-deployment-smoke-runbook.md) adımlarını canlı staging’de uygulasın; ardından Faz 151 bootstrap step 12+ ve PP evidence.
