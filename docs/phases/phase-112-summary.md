# Faz 112 Özeti: Staging Dedicated Retention Datasource Enable + E2E Evidence

Önceki faz: [phase-111-summary.md](phase-111-summary.md). Faz spec: [phase-112.md](phase-112.md).

## 1. Yapılanlar

Staging dedicated retention datasource rollout için ops/GitOps paketi: conservative default (`enabled: false` dört serviste), enable overlay örneği (key ref only), ExternalSecret naming örneği, sıralı E2E checklist (Helm → preflight → pod env → smoke → Faz 109 artifact → metrics → rollback). Staging’e workspace/search dry-run integration flag’leri eklendi (gateway smoke hizası). **Backend kodu değişmedi.**

### Staging enable durumu (net)

| Soru | Cevap |
|------|--------|
| Aktif staging values `enabled: true`? | **Hayır** — `deploy/gitops/environments/staging/values.yaml` → hepsi `false` |
| Enable overlay hazır mı? | **Evet** — `retention-datasource-enable.overlay.example.yaml` (merge secret sonrası) |
| Neden uygulanmadı? | Repo’da gerçek JDBC credential / ExternalSecret remote değerleri yok; fail-fast riski |

**Production default değişmedi** — prod GitOps hâlâ yorumlu `enabled: false` placeholder.

## 2. Backend değişiklikleri

Yok (Faz 111 binding kullanılır).

## 3. Frontend değişiklikleri

Yok.

## 4. Security/Privacy

- Git’e JDBC password, gerçek URL veya `ADMIN_ACCESS_TOKEN` yazılmadı.
- Overlay yalnızca `existingSecret` + `urlKey` / `usernameKey` / `passwordKey` adları.
- ExternalSecret örneği yalnızca placeholder `remoteRef.key` path’leri (örnek metin).
- E2E checklist pod env değerlerini ticket’a yapıştırmamayı hatırlatır.

## 5. Tests / doğrulama

| Komut | Sonuç |
|---|---|
| `bash scripts/check-no-secrets.sh` | **PASS** |
| `bash scripts/retention/ci-retention-smoke-fixtures.sh` | **PASS** (WSL) |
| `run-retention-staging-smoke.sh` (secret yok) | **PASS** — graceful skip, exit 0 |
| `bash scripts/helm-template-check.sh` | WSL’de helm yok → skip |
| Grep staging/prod GitOps retention JDBC literals | **PASS** — yalnızca key adları / `enabled: false` |
| Live staging smoke / kubectl pod env | **Çalıştırılmadı** — cluster + `RETENTION_STAGING_*` secret yok |
| Gradle retention tests | Koşulmadı — Java değişmedi |

## 6. Config/Deployment

**Aktif staging (`values.yaml`):**

```yaml
retentionDatasource:
  content:    { enabled: false, existingSecret: "", urlKey: content-retention-datasource-url, ... }
  notification: { enabled: false, ... }
  workspace:  { enabled: false, ... }
  search:     { enabled: false, ... }
```

**Enable overlay (örnek, merge edilmedi):** `retention-datasource-enable.overlay.example.yaml` → `enabled: true`, `existingSecret: notebook-platform-secrets`.

**CI evidence:** `.github/workflows/retention-readiness.yml` → `workflow_dispatch` + `run_staging_smoke=true` → artifact `retention-staging-smoke-evidence` + Step Summary (Faz 109).

## 7. Eklenen/güncellenen dosyalar

- **GitOps (yeni):** `staging/retention-datasource-enable.overlay.example.yaml`, `staging/externalsecret-retention-keys.example.yaml`
- **GitOps (düzenlenen):** `staging/values.yaml` (retentionDatasource block + workspace/search dry-run flags)
- **scripts/docs (yeni):** `scripts/retention/staging-dedicated-retention-e2e-checklist.md`
- **CI:** `retention-readiness.yml` (workflow_dispatch açıklaması)
- **docs:** `retention-datasource-ops-handoff.md`, `production-readiness.md`, `platform-retention-governance.md`, 4 RLS runbook, `phases/phase-112.md`, `phases/phase-112-summary.md`

## 8. Dedicated vs primary (özet)

| Path | Connection |
|------|------------|
| Retention count SQL | Dedicated Hikari when `*_RETENTION_DATASOURCE_ENABLED=true` |
| JPA / Flyway / user API | Primary runtime datasource — **unchanged** |

## 9. Sonraki adımlar

1. Secret manager’da 12 retention key’i oluştur + ExternalSecret sync.
2. Staging’de overlay merge + Argo sync + E2E checklist adımlarını koş.
3. `workflow_dispatch` retention-staging-smoke green run; artifact’ı change-request’e ekle.
4. Production enable ayrı faz / change-request (prod hâlâ disabled).
