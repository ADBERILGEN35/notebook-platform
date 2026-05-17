# Faz 104 Özeti: Platform Retention Service Summary + Dashboard Readiness

Önceki faz: [phase-103-summary.md](phase-103-summary.md). Faz spec: [phase-104.md](phase-104.md). Detay: [`platform-retention-dashboard-readiness.md`](../platform-retention-dashboard-readiness.md), governance: [`platform-retention-governance.md`](../platform-retention-governance.md) → "Faz 104 Service Summary ve Dashboard Readiness".

## 1. Yapılanlar

Faz 98–103 retention governance altyapısının gözlemlenebilirliğini artırmak için platform retention plan response'una servis-bazlı aggregate readiness özeti (`serviceSummaries`) eklendi. Gateway merged target list'ten content / notification / identity / audit-security domain'leri için status, sayım ve warning özeti türetir; admin UI bunu target table üstünde servis readiness kartları olarak gösterir. Operatör artık "hangi servis ready/partial/unavailable/blocked/disabled?" sorusunu tek bakışta yanıtlayabilir.

Bu faz **destructive purge, scheduler, delete veya yeni count implementation içermez**. `serviceSummaries` mevcut `targets[]` contract'ına dokunmadan eklenen optional, backward-compatible bir alandır. Metrics kararı kullanıcı onayıyla docs-only alındı: yeni gateway Micrometer kodu eklenmedi; bounded panel/alert önerileri readiness doc'unda dokümante edildi.

## 2. Backend değişiklikleri

api-gateway:

- `AdminPlatformRetentionProxyService.applyServiceSummaries(plan)` eklendi; `plan()` zincirinde `mergeContent` → `mergeNotification` sonrası çağrılır, `plan.put("serviceSummaries", …)`.
- Gruplama target'ın `service` alanından (registry-authoritative; eksikse `serviceFromKey` prefix fallback). `dataClass` `dataClassFromKey` prefix mapping (`content.`→CONTENT, `notification.`→NOTIFICATION, `identity.`→IDENTITY, `audit.`/`security.`→AUDIT_SECURITY).
- Servis başına aggregate: `totalTargets`, `dryRunReadyTargets`, `inventoryOnlyTargets`, `unavailableTargets`, `blockedTargets`, `cappedTargets`, `warningCount`, dedupe `warnings` (per-target + `serviceForWarning` ile servise map'lenen plan-level prefix'li kodlar).
- `computeServiceStatus` precedence: `ERROR > UNAVAILABLE > DISABLED > BLOCKED_BY_HOLD > PARTIAL > READY > INVENTORY_ONLY`. `ServiceSummaryAccumulator` nested helper.
- Endpoint, route, auth, scope, contract değişikliği yok; `targets[]` ve `warnings[]` aynen korunur.

identity / content / notification-service: değişiklik yok (gateway-side derive). DB/Flyway değişikliği yok.

## 3. Frontend değişiklikleri

- `features/admin/platform-retention-api.ts`: `retentionServiceSummarySchema` + `retentionPlanResponseSchema`'ya optional `serviceSummaries`, `RetentionServiceSummary` type export.
- `pages/admin/AdminPlatformRetentionPage.tsx`: warnings card öncesi "Service readiness" kart grid'i (`ServiceReadinessCard`, `ServiceStatusBadge`, `ReadinessMetric`). Her kart: service, dataClass, status badge, Total/Dry-run ready/Inventory only/Blocked by hold/Capped/Warnings, `generatedAt`, dry-run target table'a anchor (`#platform-retention-dry-run` wrapper div). Yeni status tonları (READY/PARTIAL/DISABLED/UNAVAILABLE/BLOCKED_BY_HOLD/INVENTORY_ONLY/ERROR). Purge/destructive aksiyon eklenmedi. `serviceSummaries` yoksa bölüm gizli; mevcut tablolar değişmez.

## 4. Security/Privacy

- Summary aggregate-only: note/comment/notification body, contentBlocks, recipient/user email, workspace/note/user id, legal hold reason yok. Gateway map-serialization + frontend schema PII testleriyle doğrulandı.
- Admin permission gate değişmez (`admin:retention:read`, `PLATFORM_RETENTION_GOVERNANCE_ENABLED`); dry-run-only kısıtı korunur.
- Plan-level warning'ler yalnız prefix-eşleşen servise atanır; generic warning hiçbir servise sızmaz; warning'ler dedupe edilir.
- Yeni audit event yok (yeni state-changing endpoint yok; read-only türetme). RLS etkisi yok (yeni query yok).
- Destructive aksiyon eklenmedi.

## 5. Tests

| Komut | Sonuç |
|---|---|
| `./gradlew :api-gateway:spotlessApply --no-build-cache --no-configuration-cache` | BUILD SUCCESSFUL (formatlandı) |
| `./gradlew :api-gateway:test --tests "*AdminPlatformRetention*" --no-build-cache --no-configuration-cache` | BUILD SUCCESSFUL — ProxyServiceTest 15 + ControllerTest 3 = 18, 0 failure/error/skip |
| `cd frontend && npx vitest run platform-retention-api.test.ts AdminPlatformRetentionPage.test.tsx` | 2 files, 11 passed |
| `cd frontend && npx tsc -b` | Hatasız (çıktı yok) |

Not: `--no-build-cache --no-configuration-cache` Windows-side Gradle build-cache `C:\…` path zehirlenmesine karşı (önceki fazlardaki ortamsal kök neden). Docker gerektiren RLS/Testcontainers testleri bu fazın kapsamı dışında (yeni DB query yok).

## 6. Config/Deployment

Yeni env, Helm template, GitOps override veya docker-compose değişikliği yok. `serviceSummaries` mevcut `PLATFORM_RETENTION_GOVERNANCE_ENABLED` + `CONTENT/NOTIFICATION_RETENTION_INTEGRATION_ENABLED` toggle'larına bağlı; ek flag gerekmez. Prod default `false` davranışında summary servis için `DISABLED`/`INVENTORY_ONLY` döner (beklenen, alert üretmez).

## 7. Eklenen/düzenlenen dosyalar

- **backend**: `api-gateway/.../admin/retention/AdminPlatformRetentionProxyService.java` (düzenlendi), `.../AdminPlatformRetentionProxyServiceTest.java` (6 yeni test).
- **frontend**: `frontend/src/features/admin/platform-retention-api.ts` (düzenlendi), `frontend/src/features/admin/platform-retention-api.test.ts` (2 yeni test), `frontend/src/pages/admin/AdminPlatformRetentionPage.tsx` (düzenlendi), `frontend/src/pages/admin/AdminPlatformRetentionPage.test.tsx` (1 yeni test).
- **docs (yeni)**: `docs/platform-retention-dashboard-readiness.md`, `docs/phases/phase-104-summary.md`.
- **docs (düzenlenen)**: `docs/platform-retention-governance.md` (Faz 104 bölümü), `docs/production-readiness.md` (Faz 104 readiness checks).
- **deploy/scripts**: değişiklik yok.

## 8. Kalan açıklar

- Faz 104 yeni gateway metric kodu eklemedi (kullanıcı onaylı docs-only); `platform_retention_service_summary_total{service,status}` vb. readiness doc'ta spec'lendi ama implement edilmedi.
- Workspace/search target'ları hâlâ `INVENTORY_ONLY`; summary onları registry status'üyle yansıtır, dry-run count yok (Faz 99/102 paterni dışında kaldı).
- Plan-level generic (kodsuz) warning'ler hiçbir servise atanmaz; yalnız bilinen prefix'li kodlar map'lenir — beklenen, ama özel bir servis-bağımsız uyarı eklenirse mapping güncellenmeli.
- `audit.*`/`security.*` ayrımı target'ın `service` alanına güvenir; registry bu alanı değiştirirse summary gruplaması ona uyar (kasıtlı, registry-authoritative).
- Smoke/preflight bu fazın kapsamı dışında (yeni DB path yok); production enable hâlâ Faz 101/103 runbook checklist'lerine bağlı.

## 9. Sonraki faz önerileri

1. Bounded gateway summary metric'leri (`platform_retention_service_summary_total{service,status}` vb.) + Grafana dashboard JSON — readiness doc'taki spec'i koda dökerek manuel panel kurulumunu ortadan kaldırır.
2. Workspace/search target'ları için aggregate-only dry-run count katmanı (Faz 99/102 paterni) — summary'de `INVENTORY_ONLY` kalan son domain'leri kapatır.
3. Retention preflight + smoke + service summary readiness'in CI'da Docker'lı staging job olarak otomatik doğrulanması (manuel runbook checklist yerine gated kontrol).
