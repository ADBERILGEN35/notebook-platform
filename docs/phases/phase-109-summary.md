# Faz 109 Özeti: Retention Staging Smoke Evidence + Workflow Hardening

Önceki faz: [phase-108-summary.md](phase-108-summary.md). Faz spec: [phase-109.md](phase-109.md).

## 1. Yapılanlar

`retention-staging-smoke` job'ı production-readiness için evidence üretecek şekilde sertleştirildi: domain bazlı sanitized JSON/Markdown artifact, GitHub Actions Step Summary tablosu, artifact upload ve privacy grep validasyonu. Secret yokken graceful skip ve summary'de **skipped: missing staging secrets** korundu. `retention-smoke-fixtures` davranışı değişmedi.

## 2. Backend değişiklikleri

Yok.

## 3. Frontend değişiklikleri

Yok.

## 4. Security/Privacy

- `ADMIN_ACCESS_TOKEN` log/artifact/summary'ye yazılmaz.
- Ham API response body artifact'e konmaz; orchestrator stdout yalnızca allowlist satırları gösterir.
- `write-retention-staging-report.py` mesajları forbidden pattern scan'den geçirir; şüpheli çıktı withhold edilir.
- `validate-retention-staging-artifact.sh` JSON/Markdown üzerinde Bearer/token/PII field grep yapar.
- Aggregated failure önceliği: exit `3` > `4` > `2` (Faz 108 ile aynı).

## 5. Tests

| Komut | Sonuç |
|---|---|
| `bash -n scripts/retention/run-retention-staging-smoke.sh` | CI fixtures içinde PASS |
| `bash -n scripts/retention/ci-retention-smoke-fixtures.sh` | PASS |
| `bash scripts/retention/ci-retention-smoke-fixtures.sh` (WSL) | **PASS** — fixture matrisleri + staging skip artifact + `json.tool` + grep scan |
| `RETENTION_STAGING_SMOKE_OUTPUT_DIR=... run-retention-staging-smoke.sh` (secret yok) | exit **0**, JSON/Markdown `skipped` |
| `bash scripts/check-no-secrets.sh` | PASS |
| Live staging gateway smoke | **Çalıştırılmadı** — `RETENTION_STAGING_*` secret ve staging erişimi yok |

## 6. Config/Deployment

- Yeni env: `RETENTION_STAGING_SMOKE_OUTPUT_DIR` (default `retention-staging-smoke-out/`, `.gitignore`'da).
- Artifact adı: **`retention-staging-smoke-evidence`** (JSON + Markdown).
- GitHub secrets/variables değişmedi (Faz 108 ile aynı).

## 7. Eklenen/düzenlenen dosyalar

- **scripts (yeni):** `write-retention-staging-report.py`, `validate-retention-staging-artifact.sh`, `render-retention-staging-github-summary.sh`
- **scripts (düzenlenen):** `run-retention-staging-smoke.sh`, `ci-retention-smoke-fixtures.sh`
- **CI:** `.github/workflows/retention-readiness.yml` (validate + summary + artifact adımları)
- **docs:** `phases/phase-109.md`, `phases/phase-109-summary.md`, governance, production-readiness, 4 retention runbook
- **repo:** `.gitignore` → `/retention-staging-smoke-out/`

## 8. Job summary ve artifact davranışı

### `retention-smoke-fixtures` (zorunlu)

PR + `main` push; secret gerekmez; değişiklik yok.

### `retention-staging-smoke` (opt-in)

| Durum | Job exit | Step Summary | Artifact |
|-------|----------|--------------|----------|
| Secret yok | **0** (skip) | 4 domain `skipped`, overall `skipped: missing staging secrets` | Evet (skip kayıtları) |
| Secret var, tüm domain OK / expected-gap | **0** | Domain tablosu: `passed` / `expected-gap` | Evet |
| Secret var, readiness/privacy/shape fail | **2/3/4** | İlgili domain status + sanitized message | Evet |

Summary sütunları: **Domain | Status | Exit | Expect | Message**

JSON alanları: `domain`, `status`, `exitCode`, `expectReady`, `message` (+ `overall`, `stagingSecretsConfigured`).

## 9. Kalan / sonraki

- İlk live staging run sonrası artifact örneği runbook'a eklenebilir (opsiyonel).
- Staging secret'ları tanımlanınca `workflow_dispatch` ile green run doğrulanmalı.
