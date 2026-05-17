# Faz 112: Staging Dedicated Retention Datasource Enable + End-to-End Evidence

## Hedef

Dedicated retention datasource (Faz 110–111) için staging rollout paketi: GitOps conservative default, enable overlay örneği, ExternalSecret key şablonu, E2E doğrulama checklist’i ve Faz 109 smoke evidence bağlantısı. Gerçek credential veya production enable yok.

## Scope

- Staging `values.yaml`: `retentionDatasource.*.enabled: false` (aktif)
- `retention-datasource-enable.overlay.example.yaml` (opsiyonel merge)
- `externalsecret-retention-keys.example.yaml`
- `scripts/retention/staging-dedicated-retention-e2e-checklist.md`
- Runbook / governance / production-readiness / CI workflow doc notları

## Uygulanmadı (bilinçli)

- Staging `enabled: true` — secret manager anahtarları repo dışında; overlay hazır, merge edilmedi
- Production `enabled: true`
- Live staging smoke / cluster doğrulama — ortam/secret yok

Detay: [`phase-112-summary.md`](phase-112-summary.md).
