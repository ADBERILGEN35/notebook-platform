# Faz 135 Özeti: Staging PP Evidence Live Run Orchestrator Finalization

Önceki faz: [phase-134-summary.md](phase-134-summary.md). Faz spec: [phase-135.md](phase-135.md).

## 1. Yapılanlar

Live staging PP evidence orchestrator finalize edildi: tek script ile probe → dispatch → download → validate → bundle; standart `pp-input/` dizin yapısı ve sanitized çıktılar. **Production flag açılmadı.**

| Deliverable | Path |
|-------------|------|
| Orchestrator (modlar) | `scripts/security/run-backend-live-staging-pp-evidence.sh` |
| Artifact download + wait | `scripts/security/download-backend-pp-artifacts.sh` |
| pp-input layout | `scripts/security/prepare-backend-preprod-evidence-inputs.sh --layout pp-input` |
| CI (secretless + fixture) | `ci-run-backend-live-staging-pp-evidence.sh`, `ci-prepare-backend-preprod-evidence-inputs.sh` |
| Fixture (validator uyumlu) | `scripts/security/fixtures/preprod-input/*.json` |
| Runbook / plan | [backend-live-staging-pp-evidence-run-checklist.md](../backend-live-staging-pp-evidence-run-checklist.md), [backend-staging-pp-evidence-execution-plan.md](../backend-staging-pp-evidence-execution-plan.md) |
| Gate / sign-off link | [backend-production-approval-gate.md](../backend-production-approval-gate.md), [backend-release-candidate-signoff.md](../backend-release-candidate-signoff.md) |
| Secrets setup align | [backend-staging-pp-secrets-setup.md](../backend-staging-pp-secrets-setup.md) |

## 2. Orchestrator modları

| Mod | Davranış |
|-----|----------|
| `--probe-only` | `check-staging-pp-secrets.sh`; secret yoksa **MISSING_SECRET**, dispatch yok |
| `--dispatch-github` | PP-1/PP-2 `gh workflow run` (readiness `READY_*` değilse atlanır) |
| `--download-artifacts` | `download-backend-pp-artifacts.sh` → `raw-downloads/` + `pp-input/` |
| `--build-bundle` | normalize (opsiyonel) + validate + bundle + run report |
| `--all` | probe → dispatch → download → build (operatör varsayılanı) |
| `--pp3-required true/false` | `false` (varsayılan): retention artifact zorunlu değil; `true`: PP-3 retention zorunlu |

Ek bayraklar: `--check-github`, `--wait-downloads`, `--wait-timeout`, `--output-base`, `--skip-probe`.

## 3. Standart pp-input layout ve çıktılar

**Girdi (normalize sonrası):**

- `pp-input/scim/scim-delta-sandbox-evidence.json`
- `pp-input/breakglass/break-glass-revocation-evidence.json`
- `pp-input/retention/retention-staging-smoke-results.json` (yalnızca `--pp3-required true` ise zorunlu)

**Final çıktılar (`--output-base`, varsayılan `live-pp-evidence-out/`):**

- `live-pp-evidence-run-report.md` (sanitized)
- `backend-preprod-evidence-bundle.json`
- `backend-preprod-evidence-summary.md`
- `download-manifest.json` (download adımında; run id + durum, secret yok)

## 4. Live workflow durumu

| Adım | Bu ortamda (Faz 135 sonu) |
|------|---------------------------|
| Secret probe | **MISSING_SECRET** (PP-1, PP-2) |
| `gh workflow run` dispatch | **NOT_RUN** (probe başarısız → dispatch atlandı) |
| Artifact download (live) | **NOT_RUN** |
| Bundle (fixture CI) | **GO** (synthetic pp-input) |
| Bundle (operatör, secret yok) | **NO_GO** |

Operatör secrets tanımladıktan sonra: `run-backend-live-staging-pp-evidence.sh --all --check-github --wait-downloads --provider okta --rc-id <RC>`.

## 5. Secret readiness

| PP | Readiness | Not |
|----|-----------|-----|
| PP-1 SCIM | **MISSING_SECRET** | GitHub + local env yok |
| PP-2 Break-glass | **MISSING_SECRET** | GitHub + local env yok |
| PP-3 Retention | **NOT_REQUIRED** | `pp3_required=false` (varsayılan) |

Katalog: `bash scripts/security/check-staging-pp-secrets.sh --print-required`. JSON: `--json` (değer yok).

## 6. Bundle `finalRecommendation`

| Senaryo | Sonuç |
|---------|--------|
| Secret yok / artifact yok | **NO_GO** (exit 5 veya documented readiness; asla sahte GO) |
| CI fixture (PP-1 certified + PP-2 passed) | **GO** |
| Bu review ortamı (live) | **NO_GO** (live workflow çalıştırılmadı) |

## 7. Production flag durumu

**Production flag açılmadı.** Provider mutation, break-glass production enable, retention production datasource, production scheduler ve destructive purge eklenmedi.

## 8. Privacy / check-no-secrets

| Kontrol | Sonuç |
|---------|--------|
| `bash scripts/check-no-secrets.sh` | **PASS** |
| Orchestrator / report / manifest | Secret değeri, JWT, raw payload, Authorization header **yok** |
| Artifact eksikse | **NO_GO** / **NOT_RUN** — GO gösterilmez |

## 9. Komut tablosu

| Komut | Sonuç |
|-------|--------|
| `bash scripts/check-no-secrets.sh` | **PASS** |
| `bash scripts/security/check-staging-pp-secrets.sh --print-required` | **PASS** |
| `bash scripts/security/check-staging-pp-secrets.sh --json` | **PASS** (MISSING_SECRET JSON, değer yok) |
| `bash scripts/security/ci-check-staging-pp-secrets.sh` | **PASS** |
| `bash scripts/security/ci-run-backend-live-staging-pp-evidence.sh` | **PASS** |
| `bash scripts/security/ci-prepare-backend-preprod-evidence-inputs.sh` | **PASS** |
| `bash scripts/security/ci-backend-preprod-evidence-bundle.sh` | **PASS** |
| `bash scripts/security/ci-download-backend-pp-artifacts.sh` | **PASS** (syntax) |
| `bash -n` (orchestrator + download + prepare) | **PASS** |

## Sonraki adım

1. `staging` environment secrets (Faz 134 governance): [backend-staging-pp-secrets-governance.md](../backend-staging-pp-secrets-governance.md).
2. Probe → `READY_GITHUB`: `check-staging-pp-secrets.sh --check-github`.
3. `--all --wait-downloads` ile live PP-1/PP-2 + bundle **GO**.
4. Post-run cleanup + RC sign-off güncelle.
