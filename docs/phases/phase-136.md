# Faz 136: Backend Live Staging PP Evidence Execution

## 1. Yapılanlar

Faz 135 orchestrator ile **gerçek operasyonel live run** denendi: GitHub `staging` secret probe, ardından `--all` (probe → dispatch → download → bundle). Bu ortamda PP-1/PP-2 secrets **eksik** olduğu için workflow dispatch **yapılmadı**; sanitized bundle ve run report **NO_GO** üretildi. **Production flag açılmadı.**

Önceki faz: [phase-135-summary.md](phase-135-summary.md).

## 2. Backend değişiklikleri

Runtime kod değişikliği yok. Script iyileştirmeleri:

| Değişiklik | Dosya |
|------------|--------|
| `MISSING_SECRET` iken artifact download atla | `run-backend-live-staging-pp-evidence.sh` |
| `gh run list` JSON alanı `displayTitle` → `name` | `download-backend-pp-artifacts.sh` |

## 3. Frontend değişiklikleri

Yok.

## 4. Security/Privacy

- Secret **değerleri** loglanmadı; probe/bundle yalnızca isim + durum.
- Live workflow dispatch secrets hazır olmadan **çalıştırılmadı**.
- Çıktılar: `live-pp-evidence-out/` (`.gitignore`).

## 5. Tests

| Komut | Sonuç |
|-------|--------|
| `check-staging-pp-secrets.sh --check-github --json` | MISSING_SECRET (PP-1/PP-2) |
| `run-backend-live-staging-pp-evidence.sh --all` | NO_GO, exit 5 |
| `ci-run-backend-live-staging-pp-evidence.sh` | PASS |
| `ci-backend-preprod-evidence-bundle.sh` | PASS |
| `ci-backend-rc-readiness.sh` | PASS_WITH_ENVIRONMENT_SKIPS |
| `ci-backend-docker-check.sh` | PASS |

## 6. Config/Deployment

- **RC ID:** `rc-2026-05-17`
- **Provider:** `okta` (prod GitOps’ta `retentionDatasource` disabled → PP-3 waived)
- **pp3_required:** `false` (`deploy/gitops/environments/prod/values.yaml` — retentionDatasource commented/disabled)

## 7. Eklenen/düzenlenen dosyalar

| Kategori | Path |
|----------|------|
| docs | `docs/phases/phase-136.md`, `docs/phases/phase-136-summary.md` |
| scripts | `run-backend-live-staging-pp-evidence.sh`, `download-backend-pp-artifacts.sh` |
| artifacts (local, gitignored) | `live-pp-evidence-out/*` |

## 8. Kalan açıklar

- GitHub **staging** environment secrets (PP-1/PP-2) platform/security tarafından oluşturulmalı.
- Secrets sonrası aynı `--all` komutu ile live dispatch + download + bundle **GO** hedeflenir.
- IdP cluster bearer (`scimDeltaRemoteFetch`) ayrı ESO/Vault — workflow başarısı için gerekli.

## 9. Sonraki faz önerileri

1. **Secrets provisioning** — governance runbook ile `gh secret set` / environment `staging`.
2. **Live re-run** — bundle **GO** → RC sign-off güncelle.
3. **Post-run cleanup** — rotate-after-run checklist (Faz 134 governance).

Detay: [phase-136-summary.md](phase-136-summary.md).
