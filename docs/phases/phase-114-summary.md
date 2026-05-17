# Faz 114 Özeti: Staging Dedicated Retention Datasource E2E Evidence Runbook Closure

Önceki faz: [phase-113-summary.md](phase-113-summary.md). Faz spec: [phase-114.md](phase-114.md).

## 1. Yapılanlar

**Runbook / evidence closure only** — backend ve frontend production kodu değişmedi. Staging dedicated retention datasource enable sonrası hangi kanıtların toplanacağı, change request’e ne ekleneceği ve rollout’un ne zaman **green** sayılacağı dokümante edildi. Gerçek staging cluster veya secret ile live doğrulama yapılmadı.

### Deliverables

| Öğe | Açıklama |
|-----|----------|
| E2E checklist genişletmesi | Green gates (G1–G8), evidence adımları, CR attach listesi |
| CR evidence checklist | Tüm istenen alanlar (preflight, actuator, smoke, metrics, rollback, approval) |
| Evidence format referansı | Actuator, smoke JSON/MD, Step Summary, preflight PASS/FAIL, Grafana, rollback |
| Template script | `generate-retention-e2e-evidence-template.sh` — placeholder only |
| Ops / governance / readiness / 4 runbook / CI | Faz 114 referansları |

### Staging “green” (özet)

Preflight dört domain **pass** → actuator dört servis **UP** (dedicated) → smoke dört domain **passed** (exit 0) + artifact validate → metrics reviewed (no sustained UNAVAILABLE) → ExternalSecret synced → rollback drill documented.

## 2. Backend değişiklikleri

Yok.

## 3. Frontend değişiklikleri

Yok.

## 4. Security/Privacy

- Evidence yalnızca sanitized özet: PASS/FAIL, `lastCheckStatus`, smoke `status`/`exitCode`, Grafana READY/none.
- Dokümantasyon ve template JDBC URL, password, token, raw actuator JSON, raw SQL, API body ticket’a koymayı **yasaklar**.
- Template generator çıktısı CI’da forbidden pattern grep ile taranır.
- PII / workspace name / email / note / search snippet alanları evidence formatında yok.

## 5. Tests / doğrulama

| Komut | Sonuç |
|---|---|
| `bash scripts/check-no-secrets.sh` | **PASS** |
| `bash scripts/retention/ci-retention-smoke-fixtures.sh` | **PASS** (WSL) |
| `bash -n scripts/retention/*.sh` | **PASS** (via fixtures script) |
| `generate-retention-e2e-evidence-template.sh` + grep scan | **PASS** (via fixtures script) |
| `bash scripts/retention/validate-retention-observability.sh` | **PASS** (via fixtures script) |
| Live staging enable + smoke + actuator scrape | **Çalıştırılmadı** — `RETENTION_STAGING_*` secret ve cluster yok |

## 6. Config/Deployment

- Production / staging GitOps `retentionDatasource.*.enabled: false` **değişmedi**.
- CI workflow `Retention Readiness`: Faz 114 CR artifact notu (`retention-staging-smoke-evidence` + checklist linkleri).

## 7. Eklenen/güncellenen dosyalar

**Yeni:**

- `docs/phases/phase-114.md`, `docs/phases/phase-114-summary.md`
- `scripts/retention/retention-staging-e2e-evidence-formats.md`
- `scripts/retention/staging-retention-change-request-evidence-checklist.md`
- `scripts/retention/generate-retention-e2e-evidence-template.sh`

**Güncellenen:**

- `scripts/retention/staging-dedicated-retention-e2e-checklist.md`
- `scripts/retention/ci-retention-smoke-fixtures.sh`
- `docs/retention-datasource-ops-handoff.md`
- `docs/platform-retention-governance.md`
- `docs/production-readiness.md`
- `docs/retention-rls-production-runbook.md` (+ notification, workspace, search)
- `.github/workflows/retention-readiness.yml`

**Değişmedi:** `observability/grafana/dashboards/platform-retention-readiness.json`, `observability/prometheus/alerts/platform-retention-readiness.yml` (format dokümante edildi; panel/alert tanımı zaten yeterli).

## 8. Dedicated vs primary (özet)

Faz 114 runtime davranışını değiştirmez. Evidence, enable sonrası dedicated pool + smoke/metrics ile uyumu doğrular; default hâlâ primary fallback (`enabled=false`).

## 9. Sonraki adımlar

1. Secret manager + ExternalSecret sync → staging overlay merge.
2. E2E checklist adımlarını koş; CR checklist doldur; CI `workflow_dispatch` + `run_staging_smoke=true` artifact ekle.
3. Green onay sonrası production enable kararı (ayrı CR, Faz 110–112 checklist).
