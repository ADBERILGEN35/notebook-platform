# Faz 105 Özeti: Platform Retention Metrics + Grafana Dashboard

Önceki faz: [phase-104-summary.md](phase-104-summary.md). Faz spec: [phase-105.md](phase-105.md). Detay: [`platform-retention-dashboard-readiness.md`](../platform-retention-dashboard-readiness.md) §4a/§5, governance: [`platform-retention-governance.md`](../platform-retention-governance.md) → "Faz 105 Metrics + Grafana Dashboard".

## 1. Yapılanlar

Faz 104'te eklenen `serviceSummaries` contract'ı production observability seviyesine taşındı. Api-gateway aggregation layer'a bounded-cardinality Micrometer counter'ları emit eden `PlatformRetentionMetricsPublisher` eklendi; `applyServiceSummaries` sonrası çağrılır, plan'ı mutate etmez ve hata fırlatmaz. Operatör artık content / notification / identity domain retention readiness'ini Prometheus/Grafana üzerinden tek dashboard'dan izleyebilir.

Bu faz **destructive purge, scheduler, delete veya yeni count implementation içermez**. Metric'ler additive; response contract (`targets[]`/`serviceSummaries[]`) değişmez. Tüm label'lar sabit allowlist'e map'lenir; allowlist dışı değer `unknown`/`UNKNOWN_WARNING` fallback'ine düşer — yüksek-cardinality veya PII label imkânsız. Prometheus alert dosyası kullanıcı kararıyla mevcut `notebook-platform-alerts.yml`'a dokunulmadan ayrı dosya olarak eklendi.

## 2. Backend değişiklikleri

api-gateway:

- **Yeni** `PlatformRetentionMetricsPublisher` (`@Component`, ctor `MeterRegistry`): `publish(plan)` ve `recordPlanGenerationFailure()`. `serviceSummaries`'i okuyup (mutate etmeden) ve merged `targets[]`'i tarayıp counter'ları register/increment eder; tüm gövde `try/catch RuntimeException` ile yutulur+log'lanır (metric hatası plan'ı düşürmez).
- Emit edilen counter'lar: `platform_retention_service_summary_total{service,status}`, `platform_retention_service_targets_total{service,target_status}`, `platform_retention_service_warnings_total{service,warning_code}`, `platform_retention_plan_generated_total{result}`, `platform_retention_service_blocked_targets_total{service}`, `platform_retention_service_capped_targets_total{service}`.
- Bounded label allowlist + fallback: `service`∈{content-service,notification-service,identity-service,platform}→`unknown`; `status`∈7 readiness değeri→`unknown`; `target_status`∈{DRY_RUN_READY,INVENTORY_ONLY,PURGE_READY,DISABLED,ERROR,UNAVAILABLE}→`unknown`; `warning_code` normalize sınıfı (SERVICE_UNAVAILABLE/LEGAL_HOLD_BLOCKED/COUNT_CAPPED/DRY_RUN_DISABLED/COUNT_FAILED/PARTIAL_LEGAL_HOLD_MAPPING/PLAN_INCLUDED)→`UNKNOWN_WARNING`.
- `AdminPlatformRetentionProxyService`: 6. constructor param `PlatformRetentionMetricsPublisher`; `plan()` zincirinde `applyServiceSummaries(p)` sonrası `metricsPublisher.publish(p)`; `onErrorResume`'da `metricsPublisher.recordPlanGenerationFailure()` (summary'siz, yalnız `result="error"`). Endpoint/route/auth/scope/contract değişikliği yok.
- `serviceFromKey` paket-private static reuse edilir (yeni service-mapping mantığı yok). DB/Flyway değişikliği yok. identity/content/notification-service değişmez.

## 3. Frontend değişiklikleri

Yok. Faz 105 UI feature gerektirmez; mevcut Faz 104 service readiness kartları değişmez (spec "No UI feature required").

## 4. Security/Privacy

- Metrics aggregate-only: note/comment/notification body, contentBlocks, recipient/user email, workspace/note/user id, legal hold reason/key, raw warning message, raw targetKey **label olamaz**. Forbidden label yokluğu otomatik scan ile doğrulandı.
- Tüm label değerleri sabit allowlist'e map'lenir; üst servisler yeni prefix'li kod eklese bile `warning_code` cardinality bound kalır.
- `audit.*`/`security.*` target'ları `service="identity-service"` altında raporlanır (registry-authoritative); `dataClass=AUDIT_SECURITY` `serviceSummaries` içinde korunur — ayrı service label'ı yok (doc'ta not edildi).
- Metric emission plan'ı mutate etmez, exception fırlatmaz; plan generation fail'de yalnız bounded failure metric. Response contract değişmez.
- Yeni audit event yok (yeni state-changing endpoint yok; read-only türetme). RLS etkisi yok (yeni query yok). Destructive aksiyon eklenmedi; admin permission gate + dry-run-only korunur.

## 5. Tests

| Komut | Sonuç |
|---|---|
| `./gradlew :api-gateway:spotlessApply --no-build-cache --no-configuration-cache` | BUILD SUCCESSFUL (formatlandı) |
| `./gradlew :api-gateway:test --tests "*AdminPlatformRetention*" --tests "*PlatformRetentionMetricsPublisher*" --no-build-cache --no-configuration-cache` | BUILD SUCCESSFUL — 25 test (18 mevcut AdminPlatformRetention + 7 yeni PlatformRetentionMetricsPublisher), 0 failure |
| `python3 -m json.tool platform-retention-readiness.json` | Geçerli JSON |
| `python3 yaml.safe_load platform-retention-readiness.yml` | Geçerli YAML — 1 group, 3 rule |
| Forbidden label scan (grep `userId\|email\|workspaceId\|noteId\|requestId\|targetKey\|legal-hold-key`) | Eşleşme yok (dashboard + alerts) |

Yeni testler: READY summary + target_status emit, plan-warning kaynaklı UNAVAILABLE, blocked/capped/known-warning, unknown service/status/warning→bounded fallback, bounded helper birim testleri, null/empty plan throw etmez + contract mutate etmez, `recordPlanGenerationFailure` yalnız bounded failure. İlk koşuda 1 test (`unknownWarning...`) `computeServiceStatus` daima 7 bounded status'tan birini döndürdüğü için (weird target→`PARTIAL`) düzeltildi; status="unknown" yalnız `boundedStatus` birim testiyle kapsanır.

Not: `--no-build-cache --no-configuration-cache` Windows-side Gradle build-cache `C:\…` path zehirlenmesine karşı (kayıtlı ortamsal kök neden). Docker gerektiren RLS/Testcontainers bu fazın kapsamı dışında (yeni DB path yok).

## 6. Config/Deployment

Yeni env, Helm template, GitOps override veya docker-compose değişikliği yok. Gateway zaten `spring-boot-starter-actuator` + `io.micrometer:micrometer-registry-prometheus` taşır; `MeterRegistry` auto-config ile mevcut. Metric'ler `serviceSummaries` ile aynı toggle'lara bağlı; prod default `false` davranışında counter'lar `DISABLED`/`INVENTORY_ONLY` status'üyle artar (beklenen, alert üretmez — `PlatformRetentionBlockedByLegalHoldHigh` `severity: info`). Grafana dashboard ve Prometheus alert dosyaları Faz 17 mevcut observability yükleme mekanizmasıyla pickup edilir (yeni provisioning gerekmez).

## 7. Eklenen/düzenlenen dosyalar

- **backend (yeni)**: `api-gateway/.../admin/retention/PlatformRetentionMetricsPublisher.java`.
- **backend (düzenlenen)**: `api-gateway/.../admin/retention/AdminPlatformRetentionProxyService.java` (publisher injection + plan() hook).
- **test (yeni)**: `api-gateway/.../admin/retention/PlatformRetentionMetricsPublisherTest.java` (7 test).
- **deploy/observability (yeni)**: `observability/grafana/dashboards/platform-retention-readiness.json`, `observability/prometheus/alerts/platform-retention-readiness.yml`.
- **docs (yeni)**: `docs/phases/phase-105-summary.md`.
- **docs (düzenlenen)**: `docs/platform-retention-dashboard-readiness.md` (§4a implemented metrics + §5 implemented alerts + §7 not), `docs/production-readiness.md` (Faz 105 readiness checks), `docs/platform-retention-governance.md` (Faz 105 Metrics + Grafana Dashboard bölümü).
- **frontend / scripts**: değişiklik yok.

## 8. Kalan açıklar

- Counter'lar plan generation başına artar; gerçek "anlık readiness" gauge değil — dashboard `increase()`/`rate()` ile yorumlar. Last-known readiness table `instant`+`increase([6h])` yaklaşımıdır; gauge-tabanlı snapshot gelecekte değerlendirilebilir.
- `warning_code` normalize edilir (sınıf bazlı); spesifik kod ayrımı (örn. `CONTENT_*` vs `NOTIFICATION_*` capped) `service` label'ı + panel filtresiyle yapılır, ham kod label'a yazılmaz (bilinçli cardinality kararı).
- Alert eşikleri başlangıç değeridir (`> 0`, `for: 15m/30m/1h`); production alert tuning Faz 105 scope dışı (spec gereği).
- Workspace/search target'ları hâlâ `INVENTORY_ONLY`; metric onları `target_status="INVENTORY_ONLY"` ile yansıtır, dry-run count yok (Faz 104'ten devam eden açık).
- Dashboard runbook link'leri `github.com/notebook-platform/...master` mutlak URL'lerine işaret eder; private/farklı remote'ta link host'u ortam-spesifik güncellenmelidir.

## 9. Sonraki faz önerileri

1. Workspace/search target'ları için aggregate-only dry-run count katmanı (Faz 99/102 paterni) — summary/metric'te `INVENTORY_ONLY` kalan son domain'leri kapatır ve `target_status="DRY_RUN_READY"` kapsamını tamamlar.
2. Retention preflight + smoke + service summary readiness'in CI'da Docker'lı staging job olarak otomatik doğrulanması (manuel runbook checklist yerine gated kontrol; dashboard/alert dosyalarının promtool/jsonnet lint'i de bu job'a eklenebilir).
3. Gauge-tabanlı "current readiness" metric'i + Alertmanager routing/severity tuning (env-spesifik eşikler, info→ticket / warning→page ayrımı) — Faz 105'in başlangıç eşiklerini production'a sertleştirir.
