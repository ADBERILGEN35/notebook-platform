# Faz 113: Retention DataSource Health Indicators + Safe Diagnostics

## Hedef

Dedicated retention datasource (Faz 111) için operatör görünürlüğü: secret, JDBC URL, kullanıcı adı veya ham SQL hata metni olmadan bağlantı durumunu anlamak. Normal runtime datasource ve retention count SQL path'i değişmez; destructive purge yok.

## Mekanizma

| Karar | Seçim |
|-------|--------|
| Görünürlük | **Spring Boot Actuator `HealthIndicator`** (`/actuator/health` bileşenleri) |
| Yeni internal admin REST | **Hayır** |
| Gateway platform plan contract | **Genişletilmedi** (servis-local yeterli) |

## Actuator bileşen adları

| Servis | Bean / component id |
|--------|---------------------|
| content-service | `contentRetentionDataSourceHealth` |
| notification-service | `notificationRetentionDataSourceHealth` |
| workspace-service | `workspaceRetentionDataSourceHealth` |
| search-service | `searchRetentionDataSourceHealth` |

## Güvenli detail alanları

- `retentionDatasourceEnabled` (boolean)
- `configComplete` (boolean)
- `usingDedicatedDatasource` (boolean)
- `fallbackToPrimary` (boolean)
- `poolConfigured` (boolean)
- `lastCheckStatus`: `UP` \| `DOWN` \| `DISABLED` \| `NOT_CONFIGURED` \| `FALLBACK_PRIMARY`
- `warningCodes`: sembolik kod listesi

**Asla dönülmez:** JDBC URL, username, password, host, database name, raw exception message, PII, workspace/note/search içeriği.

## Sembolik warning kodları

| Kod | Anlam |
|-----|--------|
| `RETENTION_DATASOURCE_DISABLED` | `enabled=false` (varsayılan) |
| `RETENTION_DATASOURCE_NOT_CONFIGURED` | `enabled=true` eksik config (startup fail-fast — canlıda beklenmez) |
| `RETENTION_DATASOURCE_USING_PRIMARY_FALLBACK` | Config tam ama dedicated bean yok |
| `RETENTION_DATASOURCE_CONNECTION_FAILED` | Dedicated pool var, connectivity check başarısız |

## Davranış matrisi

| Durum | `lastCheckStatus` | Aggregate component health | Connectivity check |
|-------|-------------------|---------------------------|-------------------|
| `enabled=false` | `DISABLED` | UP | Yok |
| `enabled=true` + eksik config | `NOT_CONFIGURED` | UP | Yok |
| `enabled=true` + tam config, bean yok | `FALLBACK_PRIMARY` | UP | Yok |
| `enabled=true` + pool + OK | `UP` | UP | `Connection.isValid(2)` |
| `enabled=true` + pool + fail | `DOWN` | DOWN | `isValid` SQLException yutulur |

`enabled=true` + eksik url/username/password → Faz 111 `assertCompleteIfEnabled()` startup fail-fast (canlı pod ayağa kalkmaz).

## Scope

- `*RetentionDataSourceDiagnostics` + `*RetentionDataSourceHealthIndicator` (4 servis)
- `configComplete()` on `*RetentionDataSourceProperties`
- Unit testler: disabled / not configured / fallback / UP / DOWN / no-secrets in health JSON
- Docs: ops handoff, governance, production-readiness, E2E checklist (actuator adımı), 4 RLS runbook kısa not

## Uygulanmadı

- Gateway `serviceSummaries` datasource alanı
- Production `retentionDatasource.enabled: true`
- Flyway / purge / scheduler
- `management.endpoint.health.show-details` prod'da `always` yapılmadı (`never` korunur)

Detay: [`phase-113-summary.md`](phase-113-summary.md).
