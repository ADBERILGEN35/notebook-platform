# Faz 126 Özeti: Backend Production Flag Flip Plan + Rollback Matrix

Önceki faz: [phase-125-summary.md](phase-125-summary.md). Faz spec: [phase-126.md](phase-126.md).

## 1. Yapılanlar

Production flag **flip planı**, **rollback matrix**, **GitOps CR template** ve **CI guardrail** script eklendi. **Hiçbir production flag açılmadı**; `deploy/gitops/environments/prod/values.yaml` ve chart defaults değişmedi.

| Çıktı | Path |
|-------|------|
| Flag flip plan | [backend-production-flag-flip-plan.md](../backend-production-flag-flip-plan.md) |
| Rollback matrix | [backend-production-rollback-matrix.md](../backend-production-rollback-matrix.md) |
| CR template | [backend-production-change-request-template.md](../backend-production-change-request-template.md) |
| Validator | `scripts/security/validate-production-flag-plan.sh` |
| CI | `scripts/security/ci-production-flag-plan.sh` |

## 2. Production flag durumu

| Durum | Açıklama |
|-------|----------|
| **Production flag açılmadı** | Chart + prod GitOps overlay tüm high-risk flag’lerde `false` / disabled |
| **Plan hazır** | Bundle `GO` sonrası wave 1–9 sırası dokümante |
| **CI guard** | Tehlikeli enable commit’lerini PR’da yakalar |

## 3. Rollout planı kapsamı (flag grupları)

| Grup | Kapsam |
|------|--------|
| **A — Break-glass** | `BREAK_GLASS_*`, `GATEWAY_BREAK_GLASS_DENYLIST_*` (admin write **yasak**) |
| **B — SCIM delta** | POC, remote fetch, multi-page, bearer secret ref (scheduler **yasak**) |
| **C — Retention** | Platform governance, per-domain dry-run + integration, `retentionDatasource.*` (purge **yasak**) |
| **D — Admin GitOps** | `adminGitopsPrEnabled`, live provider (auto-merge **yasak**) |
| **E — RBAC** | `adminRbac*`, `gatewayAdminRbacEnforce`, overrides (opsiyonel wave 9) |

Önerilen sıra: evidence → read-only diagnostics → BG read/revoke → denylist → SCIM dry-run fetch → retention dry-run → datasource → GitOps → RBAC enforce.

## 4. Evidence prerequisite mapping

| Wave / aile | Bundle / kanıt |
|-------------|----------------|
| Break-glass + denylist | PP-2 `pass` → `break-glass-revocation-evidence.json` |
| SCIM remote / multi-page | PP-1 `pass` → `scim-delta-sandbox-evidence.json` |
| Retention datasource | PP-3 `pass` (scope’ta ise) → `retention-staging-smoke-results.json` |
| Retention dry-run only | Domain runbook + staging smoke; PP-3 opsiyonel |
| GitOps live | Platform checklist + approved change-request |
| RBAC enforce | Security matrix (PP bundle dışı) |

CR template bundle `finalRecommendation: GO` olmadan ilerlemez.

## 5. Yasak kombinasyonlar (özet)

| ID | Yasak |
|----|--------|
| F-1 | `scimDeltaSyncEnabled=true` (scheduler) |
| F-2 | `breakGlassAllowAdminWrite` veya `gatewayBreakGlassAdminAllowed=true` |
| F-3 | Remote fetch + `scimDeltaDryRunOnly=false` |
| F-4 | `retentionDatasource.*.enabled` kanıtsız |
| F-5 | Integration, dry-run stabil değilken |
| F-6 | Purge UI / worker without dry-run-only |
| F-7 | GitOps auto-merge |
| F-8 | Çoklu aile tek CR’de onaysız |

CI: `validate-production-flag-plan.sh` + unsafe fixture FAIL testi.

## 6. Rollback matrix (özet)

Her aile için: GitOps flag revert → Argo rollback (gerekirse) → pod restart (datasource’da zorunlu) → denylist cache TTL bekleme → smoke. Detay: [backend-production-rollback-matrix.md](../backend-production-rollback-matrix.md).

## 7. Privacy / secrets

- Prod flip plan dokümanlarında secret/token yok.
- `check-no-secrets.sh` — **PASS**
- Validator yalnızca boolean flag durumlarını okur.

## 8. Tests / doğrulama

| Komut | Sonuç |
|-------|--------|
| `bash scripts/security/ci-production-flag-plan.sh` | **PASS** |
| `bash scripts/check-no-secrets.sh` | **PASS** |
| `bash scripts/security/ci-backend-production-readiness-review.sh` | **PASS** |
| `bash scripts/security/ci-backend-preprod-evidence-bundle.sh` | (önceki faz) **PASS** |
| `bash scripts/helm-template-check.sh` | **SKIP** (helm yok, local) |
| Backend/frontend runtime | **Değişiklik yok** |

## 9. Sonraki adım

1. Staging’de PP-1..PP-3 tamamla; bundle `GO` al.
2. [backend-production-change-request-template.md](../backend-production-change-request-template.md) ile wave 1 CR aç.
3. GitOps PR’da `validate-production-flag-plan.sh` çalıştır; merge sonrası rollback matrix’e göre smoke.
4. Her wave için ayrı CR; forbidden kombinasyonlara uy.
5. **Faz 127:** `ci-backend-rc-readiness.sh` veya **Backend RC Readiness** workflow ile RC gate `PASS`.
