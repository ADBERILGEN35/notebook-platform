# Faz 110 Özeti: Dedicated Retention Datasource Helm Template + Ops Handoff

Önceki faz: [phase-109-summary.md](phase-109-summary.md). Faz spec: [phase-110.md](phase-110.md).

## 1. Yapılanlar

Retention dry-run count path'leri için Helm/GitOps **ops template** eklendi: servis başına `retentionDatasource.<service>` (`enabled`, `existingSecret`, `urlKey`, `usernameKey`, `passwordKey`). Deployment'lara opsiyonel `*_RETENTION_DATASOURCE_*` env wiring; DBA handoff dokümanı; GitOps'ta yalnızca yorumlu placeholder. **Runtime binding yok** — uygulama kodu bu env'leri okumaz.

## 2. Backend değişiklikleri

Yok (bilinçli — future binding).

## 3. Frontend değişiklikleri

Yok.

## 4. Security/Privacy

- Chart/GitOps içinde JDBC password/username gerçek değeri yok.
- Secret yalnızca `existingSecret` + key referansları; ExternalSecret örneği dokümante.
- Prod/default: tüm `retentionDatasource.*.enabled: false`.
- Normal `DB_*` / `DB_RUNTIME_*` datasource davranışı değişmedi.
- DBA role SQL örnekleri **dokümantasyon**; repo migration değil.

## 5. Tests

| Komut | Sonuç |
|---|---|
| `bash scripts/helm-template-check.sh` (WSL, helm mevcut ise) | Chart lint + render (default, dev, prod example, retention enabled overlay) |
| `bash scripts/check-no-secrets.sh` | PASS |
| Grep values/templates retention password literals | PASS — yalnızca key adları ve placeholder `<from-secret-manager>` handoff doc'ta |
| Backend unit/integration | Koşulmadı — kod değişmedi |

## 6. Config/Deployment

| Alan | Prod default | Not |
|------|--------------|-----|
| `retentionDatasource.content.enabled` | `false` | Ayrı dry-run flag'lerden bağımsız |
| `retentionDatasource.notification.enabled` | `false` | |
| `retentionDatasource.workspace.enabled` | `false` | |
| `retentionDatasource.search.enabled` | `false` | |

GitOps dev/staging/prod: yorumlu `retentionDatasource` blokları; secret değeri yok.

## 7. Helm values / templates değişiklikleri

| Dosya | Değişiklik |
|-------|------------|
| `values.yaml` | `retentionDatasource` blok (4 servis, default disabled + key adları) |
| `templates/_helpers.tpl` | `retentionDatasourceEnv`, `retentionDatasourceSecretName` |
| `templates/deployment-content.yaml` | Opsiyonel CONTENT retention env |
| `templates/deployment-notification.yaml` | NOTIFICATION retention env |
| `templates/deployment-workspace.yaml` | WORKSPACE retention env |
| `templates/deployment-search.yaml` | SEARCH retention env |
| `examples/retention-datasource/README.md` | ExternalSecret / values örneği |
| `scripts/helm-template-check.sh` | Retention enabled render |

**Ops / future binding (net):** Helm Faz 110 yalnızca **ops template + pod env pre-wire**. Count query'leri hâlâ mevcut runtime pool üzerinden; dedicated datasource **çalışmıyor** until backend faz.

## 8. Ops handoff özeti

Doküman: [`retention-datasource-ops-handoff.md`](../retention-datasource-ops-handoff.md)

- DBA role örnekleri (content/notification/workspace/search)
- SELECT-only minimum privilege
- BYPASSRLS vs `row_security=off` matrisi
- Preflight SQL sırası: content → notification → workspace → search
- Smoke sırası: 4 gateway script veya `run-retention-staging-smoke.sh`
- Rollback: datasource disabled → integration false → dry-run false

## 9. Sonraki faz önerileri

1. Backend retention `DataSource` bean binding (`*_RETENTION_DATASOURCE_*` env okuma).
2. İlk staging/prod enable sonrası runbook'a örnek artifact/smoke evidence linki.
3. Helm subchart veya operator ile role provisioning (opsiyonel; hâlâ migration repo dışı).
