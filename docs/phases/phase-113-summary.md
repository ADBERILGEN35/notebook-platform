# Faz 113 Özeti: Retention DataSource Health Indicators + Safe Diagnostics

Önceki faz: [phase-112-summary.md](phase-112-summary.md). Faz spec: [phase-113.md](phase-113.md).

## 1. Yapılanlar

Dört serviste dedicated retention datasource için **Spring Boot Actuator `HealthIndicator`** eklendi. Operatör, secret veya JDBC bilgisi görmeden `enabled`, dedicated pool kullanımı, fallback ve sembolik warning kodlarını okuyabilir. Connectivity check yalnızca dedicated `DataSource` bean varken çalışır: `Connection.isValid(2)` (read-only, minimal). Gateway platform retention plan contract **genişletilmedi**.

### Görünürlük (net)

| Soru | Cevap |
|------|--------|
| Actuator mı, internal admin endpoint mi? | **Actuator** — bileşenler `/actuator/health` altında |
| Gateway plan’a datasource health? | **Hayır** — servis-local + docs |
| `show-details` | `never` (mevcut `application.yml`) — detail alanları yine de secret içermez |

### `enabled=false` (production default)

| Alan | Değer |
|------|--------|
| `lastCheckStatus` | `DISABLED` |
| `fallbackToPrimary` | `true` |
| `warningCodes` | `RETENTION_DATASOURCE_DISABLED` |
| Bileşen aggregate health | **UP** (beklenen ops durumu) |

Retention count repository primary runtime `DataSource` kullanır (Faz 111 davranışı değişmedi).

### `enabled=true` + geçerli config + dedicated pool

| Alan | Değer (bağlantı OK) |
|------|---------------------|
| `lastCheckStatus` | `UP` |
| `usingDedicatedDatasource` | `true` |
| `fallbackToPrimary` | `false` |
| `poolConfigured` | `true` |
| Bileşen health | **UP** |

Bağlantı hatası: `lastCheckStatus=DOWN`, `RETENTION_DATASOURCE_CONNECTION_FAILED`, bileşen **DOWN**; ham SQLException metni response’ta yok.

### `enabled=true` + eksik config

Startup **fail-fast** (Faz 111) — pod başlamaz. Health indicator `NOT_CONFIGURED` yolu yalnızca unit test / teorik; canlıda görülmemeli.

## 2. Backend değişiklikleri

| Servis | Diagnostics | HealthIndicator bean |
|--------|-------------|----------------------|
| content | `ContentRetentionDataSourceDiagnostics` | `contentRetentionDataSourceHealth` |
| notification | `NotificationPlatformRetentionDataSourceDiagnostics` | `notificationRetentionDataSourceHealth` |
| workspace | `WorkspaceRetentionDataSourceDiagnostics` | `workspaceRetentionDataSourceHealth` |
| search | `SearchRetentionDataSourceDiagnostics` | `searchRetentionDataSourceHealth` |

- `*RetentionDataSourceProperties.configComplete()` eklendi; `assertCompleteIfEnabled()` buna delegasyon.
- Retention count SQL / `JdbcTemplate` qualifier path **değişmedi**.

## 3. Frontend değişiklikleri

Yok.

## 4. Security/Privacy

- Health detail: JDBC URL, username, password, host, DB adı, raw SQL exception **yok**.
- Connectivity failure → yalnızca `RETENTION_DATASOURCE_CONNECTION_FAILED`.
- Testler `assertHealthHasNoSecrets` ile `jdbc`, `password`, `secret`, exception sınıf adı vb. yoklar.
- PII / workspace / note / search içeriği dönülmez.
- Production default `*_RETENTION_DATASOURCE_ENABLED=false` **değişmedi**.

## 5. Tests / doğrulama

| Komut | Sonuç |
|---|---|
| `./gradlew :content-service:test --tests "*Retention*"` | **PASS** |
| `./gradlew :notification-service:test --tests "*platformretention*"` | **PASS** |
| `./gradlew :workspace-service:test --tests "*WorkspaceRetention*"` | **PASS** |
| `./gradlew :search-service:test --tests "*SearchRetention*"` | **PASS** |
| `spotlessApply` (4 servis) | **PASS** |
| `bash scripts/check-no-secrets.sh` | **PASS** |
| Live cluster actuator scrape | **Çalıştırılmadı** — staging cluster/secret yok |

Yeni testler (servis başına): `*RetentionDataSourceHealthIndicatorTest` — disabled, not configured, fallback primary, UP (`isValid`), DOWN (connection fail), health JSON’da secret yok.

## 6. Config/Deployment

- Helm / GitOps retention `enabled: false` **aynı** (Faz 112).
- Actuator erişimi mevcut platform güvenlik modeli ile (internal network / auth); `show-details: never` korunur.
- Staging E2E checklist’e opsiyonel actuator bileşen kontrolü eklendi.

## 7. Eklenen/güncellenen dosyalar

**Main (servis başına):**

- `*RetentionDataSourceDiagnostics.java`
- `*RetentionDataSourceHealthIndicator.java`
- `*RetentionDataSourceProperties.java` (`configComplete`)

**Test:**

- `ContentRetentionDataSourceHealthIndicatorTest.java`
- `NotificationPlatformRetentionDataSourceHealthIndicatorTest.java`
- `WorkspaceRetentionDataSourceHealthIndicatorTest.java`
- `SearchRetentionDataSourceHealthIndicatorTest.java`

**Docs:**

- `docs/phases/phase-113.md`, `docs/phases/phase-113-summary.md`
- `docs/retention-datasource-ops-handoff.md` (Faz 113 actuator bölümü)
- `docs/platform-retention-governance.md`, `docs/production-readiness.md`
- `scripts/retention/staging-dedicated-retention-e2e-checklist.md`
- 4 RLS runbook — dedicated datasource altında Faz 113 health notu

## 8. Dedicated vs primary (özet)

| Path | Faz 113 etkisi |
|------|----------------|
| Retention count SQL | Değişmedi — Faz 111 dedicated/primary seçimi |
| JPA / Flyway / user API | Değişmedi |
| Operatör görünürlüğü | Yeni actuator bileşeni — diagnostic only |

## 9. Sonraki adımlar

1. Staging’de overlay merge + secret sonrası E2E checklist (actuator bileşen `UP` + smoke).
2. İsteğe bağlı: gateway plan’a optional datasource health (backward-compatible) — şu an gerekli değil.
3. Production enable kararı önceki Faz 110–112 checklist ile (health ek doğrulama adımı).
