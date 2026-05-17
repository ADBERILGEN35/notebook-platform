# Faz 111 Özeti: Backend Retention DataSource Binding

Önceki faz: [phase-110-summary.md](phase-110-summary.md). Faz spec: [phase-111.md](phase-111.md).

## 1. Yapılanlar

Dört serviste retention dry-run **count repository** katmanına optional dedicated Hikari `DataSource` binding eklendi. Helm Faz 110 `*_RETENTION_DATASOURCE_*` env isimleri Spring `application.yml` ile eşleştirildi. **Runtime'da gerçekten çalışır:** `enabled=true` + tam config → count SQL dedicated pool; `enabled=false` → primary runtime datasource (önceki davranış).

## 2. Backend değişiklikleri

| Servis | Properties | Config | Repository |
|--------|------------|--------|------------|
| content | `ContentRetentionDataSourceProperties` | `ContentRetentionJdbcTemplateConfig` | `ContentRetentionCountRepository` |
| notification | `NotificationPlatformRetentionDataSourceProperties` | `NotificationPlatformRetentionJdbcTemplateConfig` | `NotificationPlatformRetentionCountRepository` |
| workspace | `WorkspaceRetentionDataSourceProperties` | `WorkspaceRetentionJdbcTemplateConfig` | `WorkspaceRetentionCountRepository` |
| search | `SearchRetentionDataSourceProperties` | `SearchRetentionJdbcTemplateConfig` | `SearchRetentionCountRepository` |

- Dedicated pool: max 2 connections, `initializationFailTimeout=-1` (lazy connect).
- Fail-fast: `assertCompleteIfEnabled()` bean oluşturulurken — eksik url/username/password → `IllegalStateException` (değer loglanmaz).
- JPA / Flyway / normal repository path'leri değişmedi.

## 3. Frontend değişiklikleri

Yok.

## 4. Security/Privacy

- Password/URL log, metric, audit veya exception mesajına yazılmaz.
- Testlerde `placeholder` / mock — gerçek secret yok.
- RLS otomatik açılmaz; BYPASSRLS role migration eklenmedi.
- Production default: `CONTENT_RETENTION_DATASOURCE_ENABLED=false` (ve diğer servisler).

## 5. Tests

| Komut | Sonuç |
|---|---|
| `./gradlew :content-service:test --tests "*Retention*"` | **PASS** |
| `./gradlew :notification-service:test --tests "*platformretention*"` | **PASS** |
| `./gradlew :workspace-service:test --tests "*WorkspaceRetention*"` | **PASS** |
| `./gradlew :search-service:test --tests "*SearchRetention*"` | **PASS** |
| `spotlessApply` (4 servis) | **PASS** |
| `bash scripts/check-no-secrets.sh` | **PASS** |
| `bash scripts/helm-template-check.sh` | WSL'de helm yok → skip |

Yeni testler (servis başına): disabled → retention bean yok; enabled + eksik url → context fail; enabled + tam config → `*RetentionJdbcTemplate` bean; repository dedicated template kullanımı (mock verify). Mevcut plan service testleri geçti.

## 6. Config/Deployment

`application.yml` (her servis):

```yaml
# örnek content
content.retention.datasource:
  enabled: ${CONTENT_RETENTION_DATASOURCE_ENABLED:false}
  url: ${CONTENT_RETENTION_DATASOURCE_URL:}
  username: ${CONTENT_RETENTION_DATASOURCE_USERNAME:}
  password: ${CONTENT_RETENTION_DATASOURCE_PASSWORD:}
```

Helm `retentionDatasource.*` env wiring (Faz 110) aynı env isimlerini besler. Prod GitOps default hâlâ disabled.

## 7. Davranış özeti (net)

| Durum | Davranış |
|-------|----------|
| `enabled=false` | Count repository `new JdbcTemplate(primary DataSource)` — **fallback**, Faz 111 öncesi ile aynı |
| `enabled=true` + URL/user/password dolu | Ayrı Hikari pool; count repository **dedicated** `JdbcTemplate` kullanır — **runtime aktif** |
| `enabled=true` + eksik config | Startup **fail-fast** (`IllegalStateException`, hangi alan eksik listelenir, secret yok) |

Dry-run/integration flag'leri (`*_DRY_RUN_*`, gateway integration) datasource enable'dan bağımsız.

## 8. Docs

Güncellenen: `retention-datasource-ops-handoff.md`, `platform-retention-governance.md`, `production-readiness.md`, dört retention RLS runbook, Helm README + `examples/retention-datasource/README.md`, `values.yaml` yorumu.

## 9. Sonraki faz önerileri

1. Staging'de `retentionDatasource.enabled=true` + DBA role ile uçtan uca count doğrulama (smoke + metrics).
2. Connection pool health indicator (opsiyonel) dedicated pool için.
3. Notification retention **worker** JDBC path'i bilinçli olarak bu fazda değişmedi — yalnızca platform retention count repository.
