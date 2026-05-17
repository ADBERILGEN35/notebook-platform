# Faz 125 Özeti: Backend Pre-prod Evidence Bundle + Approval Gate

Önceki faz: [phase-124-summary.md](phase-124-summary.md). Faz spec: [phase-125.md](phase-125.md).

## 1. Yapılanlar

Backend pre-prod kanıtlarını (PP-1..PP-3) tek sanitized pakette birleştiren builder, approval gate dokümantasyonu, CI fixture’ları ve `workflow_dispatch` workflow eklendi. **Runtime kod değişmedi**; production flag açılmadı.

| Çıktı | Path |
|-------|------|
| Bundle format | [backend-preprod-evidence-bundle.md](../backend-preprod-evidence-bundle.md) |
| Approval gate | [backend-production-approval-gate.md](../backend-production-approval-gate.md) |
| Builder | `scripts/security/build-backend-preprod-evidence-bundle.sh` |
| Aggregator | `scripts/security/build_backend_preprod_evidence_bundle.py` |
| CI | `scripts/security/ci-backend-preprod-evidence-bundle.sh` |
| Fixtures | `scripts/security/fixtures/preprod-input/` |
| Workflow | `.github/workflows/backend-preprod-evidence-bundle.yml` |

## 2. Backend readiness recommendation mapping

| Faz 124 verdict | Faz 125 bundle davranışı |
|-----------------|-------------------------|
| **READY_WITH_PREPROD_BLOCKERS** | Canlı staging input yokken builder **`NO_GO`** üretir (beklenen) |
| Merge / ship artifacts **GO** | Değişmedi — chart default CI ayrı |
| Production flag flip **NO-GO** | `finalRecommendation: GO` olana kadar gate kapalı |

| `finalRecommendation` | Anlam |
|-------------------------|--------|
| `GO` | PP-1 certified, PP-2 passed, PP-3 pass veya `not_required`, accepted risk yok |
| `GO_WITH_ACCEPTED_RISKS` | Gate’ler pass; `acceptedRisks` veya `highRiskCount > 0` |
| `NO_GO` | Eksik/fail PP, privacy, shape mismatch |

## 3. PP-1 / PP-2 / PP-3 evidence yorumu

| Gate | Pass kriteri | Bu ortamda |
|------|--------------|------------|
| **PP-1 SCIM** | `certificationResult: certified` | CI fixture **pass**; **canlı staging sandbox kanıtı yok** |
| **PP-2 Break-glass** | `result: passed` + `BREAK_GLASS_TOKEN_REVOKED` | CI fixture **pass**; **canlı staging drill kanıtı yok** |
| **PP-3 Retention** | Tüm domain `passed` (required ise) | Default **`not_required`**; PP-3 required + retention eksik → **NO_GO** (CI doğrulandı) |

Input yokken bundle: PP-1/PP-2 **`missing`**, PP-3 **`not_required`**, `blockerCount ≥ 2`, **`NO_GO`**.

## 4. Live staging evidence durumu

**Bu review ortamında canlı staging artifact’leri yok.** Upstream workflow’lar (`scim-delta-readiness`, `break-glass-revocation-readiness`, `retention-readiness`) staging secret’ları ile manuel `workflow_dispatch` çalıştırılmalı; çıkan JSON’lar bundle builder’a verilmeli.

Operatör adımları: [backend-production-approval-gate.md](../backend-production-approval-gate.md).

## 5. Üretilen bundle artifact adları

| Artifact | İçerik |
|----------|--------|
| `backend-preprod-evidence-bundle.json` | Aggregate schema `backend-preprod-evidence-bundle-v1` |
| `backend-preprod-evidence-summary.md` | CR markdown özeti |
| GitHub upload (workflow) | Artifact adı: **`backend-preprod-evidence-bundle`** |

Upstream input dosya adları (normalize):

- `scim-delta-sandbox-evidence.json`
- `break-glass-revocation-evidence.json`
- `retention-staging-smoke-results.json`

## 6. Privacy / secret güvenliği

- Bundle çıktısı forbidden-pattern grep’ten geçer (Bearer, eyJ, access_token, Authorization, password, secret, jdbc:, rawPayload, nextCursor, @odata.nextLink, emergency token).
- Fixture ve builder çıktılarında ham token/JWT/SCIM body yok.
- `check-no-secrets.sh` — **PASS**
- Privacy ihlali → exit **3**, `finalRecommendation: NO_GO`

## 7. Approval gate (özet)

| Kural | Sonuç |
|-------|--------|
| PP-1 missing / not certified | NO_GO |
| PP-2 missing / not passed | NO_GO |
| PP-3 required + missing/fail | NO_GO |
| privacy / shape mismatch | NO_GO |
| accepted risks + pass | GO_WITH_ACCEPTED_RISKS |
| tüm required pass | GO |

Tam tablo: [backend-production-approval-gate.md](../backend-production-approval-gate.md).

## 8. Tests / doğrulama

| Komut | Sonuç |
|-------|--------|
| `bash scripts/security/ci-backend-preprod-evidence-bundle.sh` | **PASS** (missing→NO_GO, all-pass→GO, PP-3 required→NO_GO) |
| `bash scripts/check-no-secrets.sh` | **PASS** |
| `bash scripts/security/ci-backend-production-readiness-review.sh` | **PASS** |
| `bash scripts/scim/ci-scim-delta-remote-fetch-fixtures.sh` | **PASS** |
| `bash scripts/security/ci-break-glass-revocation-fixtures.sh` | **PASS** |
| `bash scripts/retention/ci-retention-smoke-fixtures.sh` | **PASS** |
| `bash -n scripts/security/build-backend-preprod-evidence-bundle.sh` | **PASS** (CI içinde) |
| Backend/frontend runtime | **Değişiklik yok** |

## 9. Sonraki adım

1. Staging’de PP-1..PP-3 workflow’larını çalıştır; artifact’leri indir.
2. **Backend Pre-prod Evidence Bundle** workflow (`workflow_dispatch`) veya local builder ile bundle üret; CR’ye `backend-preprod-evidence-summary.md` + JSON ekle.
3. `finalRecommendation: GO` (veya imzalı `GO_WITH_ACCEPTED_RISKS`) olmadan production flag flip yapma.
4. Retention datasource rollout scope’unda ise `pp3_required: true` ile bundle oluştur.
5. **Faz 126:** Bundle `GO` sonrası [`backend-production-flag-flip-plan.md`](../backend-production-flag-flip-plan.md) ve CR template ile wave bazlı GitOps flip.
