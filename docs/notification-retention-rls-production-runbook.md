# Notification Retention RLS Production Runbook (Faz 103)

Faz 102'de notification-service'e eklenen aggregate-only retention dry-run count akışı (`GET /internal/admin/retention/notification/plan`) cross-workspace bir aggregate sorgu üretir. Production'da RLS aktifken bu akış standart tenant-scoped runtime role ile çalışmaz; bu runbook hangi DB role, env, preflight ve smoke adımlarıyla güvenle production'a açılacağını tanımlar.

Bu runbook destructive bir aksiyon önermez. Notification data delete, retention worker davranışı, audit purge, object storage lifecycle ve eDiscovery export kapsam dışıdır.

İlgili docs:

- [`platform-retention-governance.md`](platform-retention-governance.md) → "Faz 102 Notification-service Integration", "Faz 103 Notification RLS Readiness"
- [`notification-retention-worker.md`](notification-retention-worker.md) (Faz 83 worker — ayrı endpoint)
- [`retention-rls-production-runbook.md`](retention-rls-production-runbook.md) (Faz 101 content paterni)
- [`database-roles-and-rls.md`](database-roles-and-rls.md), [`internal-service-auth.md`](internal-service-auth.md), [`runtime-rls-rollout.md`](runtime-rls-rollout.md)

## 1. Amaç ve Kapsam

- Kapsam: notification-service `notification_delivery_analytics_hourly`, `notification_fanout_outbox`, `notification_dead_letter_requeue_requests`, `notification_digest_items`, `email_notifications` üzerinde aggregate-only count.
- Amaç: production'da notification retention dry-run akışını yanlış konfigürasyon nedeniyle yanıltıcı sıfır count, raw SQL hatası veya cross-tenant kaçak olmadan çalıştırmak.
- Out of scope: destructive purge, Faz 83 retention worker davranışını değiştirme, archive-before-delete, per-tenant retention policy, RLS modelinin yeniden tasarlanması.

## 2. Faz 83 Worker vs Faz 102 Platform Dry-run Farkı

İki ayrı endpoint yan yana çalışır; operatör karıştırmamalıdır:

| | Faz 83 worker | Faz 102 platform dry-run |
|---|---|---|
| Endpoint | `POST/GET /internal/admin/notifications/retention/*` | `GET /internal/admin/retention/notification/plan` |
| Service JWT scope | `internal:admin:notifications:retention:read` (+ run) | `internal:admin:retention:read` |
| Amaç | Bounded, audited **purge** + dry-run plan | Platform contract **aggregate count** (dry-run only) |
| Destructive | `dryRun=false` + manuel run flag ile mümkün | **Hiç yok** |
| Bu runbook | Kapsam dışı (bkz. `notification-retention-worker.md`) | **Kapsam içi** |

Bu runbook yalnızca Faz 102 platform dry-run akışını ele alır. Faz 83 worker schedule, purge limits ve Faz 84 legal-hold davranışı **değişmez**.

## 3. Neden Normal RLS Tenant Context Yeterli Değil?

Notification retention count'ları platform-wide aggregate üretir. Workspace policy'leri `app.current_workspace_id` set edilmemiş bir oturumda satırları silently filtreler ve aggregate count sıfır döner. Normal tenant role ile çalıştırılırsa:

- Plan response yanıltıcı 0 count gösterir.
- Operatör notification retention politikasının tetiklenmediğini/tetiklendiğini yanlış değerlendirir.
- Audit'te yanlış "no eligible rows" izi kalır.

Sonuç: notification retention, ayrı ve BYPASSRLS'li ya da `row_security=off` ile çalışan bir bağlantı gerektirir.

## 4. Dedicated Retention DB Role Önerisi

Önerilen rol: `notebook_notification_retention`.

Karakteristikler:

- `rolcanlogin = true`
- `rolbypassrls = true` *veya* runtime'da `SET row_security TO off` ile bağlanma
- `rolsuper = false`
- `rolcreatedb = false`, `rolcreaterole = false`
- Sadece `SELECT` privilege; `INSERT/UPDATE/DELETE` verilmemeli.

Migration owner veya superuser ile setup örneği (gerçek parola placeholder; secret manager'dan verilir):

```sql
CREATE ROLE notebook_notification_retention LOGIN PASSWORD '<replace-with-secret>' BYPASSRLS;
GRANT CONNECT ON DATABASE notebook_platform TO notebook_notification_retention;
GRANT USAGE ON SCHEMA public TO notebook_notification_retention;
GRANT SELECT ON
  notification_delivery_analytics_hourly,
  notification_fanout_outbox,
  notification_dead_letter_requeue_requests,
  notification_digest_items,
  email_notifications
TO notebook_notification_retention;
```

`BYPASSRLS` istenmiyorsa alternatif: aynı role'e sadece `SELECT` GRANT'leri verilir ve connection string'inde `options=-c row_security=off` ile bağlanılır. Bu yaklaşım yalnız retention bağlantısı için kullanılmalı; başka servis bu connection'ı paylaşmamalıdır.

## 5. BYPASSRLS vs `row_security=off` Trade-off

| Yöntem | Avantaj | Risk |
|---|---|---|
| `BYPASSRLS` role | Tek nokta config, role-bound | Role kazara başka servise verilirse tüm RLS atlatılır; rotasyon dikkat ister |
| `row_security=off` connection option | Role flag değişmez; yalnız bağlantı opt-out | Connection pool config gerekiyor; yanlış konfigde sessizce default'a döner |

Karar kriterleri:

- DBA tek rol prensibi tercih ediyor → BYPASSRLS.
- Production rolleri minimum capability ile sınırlanmalı → `row_security=off` connection option + plain SELECT GRANT.

Her iki yöntem de yalnızca notification retention path'i için kullanılmalıdır; başka servis paylaşmamalıdır.

## 6. Minimum DB Privilege Set

| Object | Privilege |
|---|---|
| `notebook_platform` DB | `CONNECT` |
| `public` schema | `USAGE` |
| `notification_delivery_analytics_hourly` | `SELECT` |
| `notification_fanout_outbox` | `SELECT` |
| `notification_dead_letter_requeue_requests` | `SELECT` |
| `notification_digest_items` | `SELECT` |
| `email_notifications` | `SELECT` |

Diğer tüm tablo / object'ler için privilege verilmemeli. `INSERT/UPDATE/DELETE/TRUNCATE/ALTER` yetkisi yok.

## 7. Notification Tabloları İçin Readiness Checks

Script: [`scripts/retention/check-notification-retention-rls-readiness.sql`](../scripts/retention/check-notification-retention-rls-readiness.sql)

Çalıştırma (migration owner veya privileged inspection role ile):

```bash
psql "$DB_MIGRATION_URL" \
  -v retention_role=notebook_notification_retention \
  -f scripts/retention/check-notification-retention-rls-readiness.sql
```

Beklenen sonuçlar:

- Section 1: `rolname=notebook_notification_retention`, `bypasses_rls=true` (veya `row_security=off` yaklaşımı seçildiyse `false` + connection option). `is_superuser=false`, `can_create_db/role=false`.
- Section 2: `row_security` ayarı bilgilendirici; non-BYPASSRLS retention bağlantısı için `off` olmalı.
- Section 3: yalnız `can_select=true`; `can_insert/update/delete=false` (5 tablo).
- Section 4: Faz 83 indexleri (`idx_notification_delivery_analytics_bucket`, `idx_fanout_outbox_status_next`, `idx_digest_items_status_scheduled`, `idx_email_status_next_attempt`) listede görünür; dead-letter tablosu `created_at` predicate'i ile çalışır (index informational).
- Section 5: RLS hedef tablolarda `rls_enabled=true` ise retention role BYPASSRLS olmalı veya bağlantı `row_security=off` ile gelmeli.
- Section 6: bounded probe count permission_denied vermez; sıfır row da kabul (boş tablo).

Herhangi bir bölüm beklenmeyen sonuç verirse production rollout başlatılmamalıdır.

## 8. Service JWT Trust Gereksinimleri

Faz 102'den gelen `internal:admin:retention:read` scope'lu service JWT zorunluluğu (audience `notification-service`) korunur. Production'a alma için:

- Gateway side `gateway.admin.platform-retention.notification.enabled=true` yapıldığında signing key gateway'in mevcut service JWT signer'ından gelir.
- Service JWT trust config gateway signing kid ile coordinated rotate edilir.
- Rotasyon prosedürü için bkz. [`internal-service-auth.md`](internal-service-auth.md) ve [`admin-rbac-override-reload.md`](admin-rbac-override-reload.md).

## 9. Smoke Test Adımları

Script: [`scripts/retention/notification-retention-dry-run-smoke.sh`](../scripts/retention/notification-retention-dry-run-smoke.sh)

Çalıştırma:

```bash
API_BASE_URL=https://api.example.com \
ADMIN_ACCESS_TOKEN="<admin JWT with admin:retention:read>" \
EXPECT_NOTIFICATION_RETENTION_READY=true \
bash scripts/retention/notification-retention-dry-run-smoke.sh
```

Script:

- `/admin/retention/platform/plan?dryRun=true` çağırır.
- 6 notification target'ının (`notification.analytics_hourly`, `notification.fanout_outbox_sent`, `notification.fanout_outbox_dead`, `notification.dead_letter_requeue_requests`, `notification.digest_items_terminal`, `notification.email_notifications_terminal`) plan'da göründüğünü doğrular.
- `status`, `eligibleCount` (null|≥0), `purgeableCount` (≥0), `blockedByLegalHold` shape'lerini kontrol eder.
- Response içinde raw payload/body/recipient/email/userId/workspaceId substring'lerinin geçmediğini doğrular.
- `EXPECT_NOTIFICATION_RETENTION_READY=false` (default): unavailable/disabled durumu beklenen kabul edilir → exit 0 (rapor).
- `EXPECT_NOTIFICATION_RETENTION_READY=true`: gap → exit 2.

Exit kodları:

| Kod | Anlam |
|---|---|
| `0` | Smoke başarılı |
| `2` | `NOTIFICATION_RETENTION_SERVICE_UNAVAILABLE` / `NOTIFICATION_RETENTION_DB_PERMISSION_DENIED` / `NOTIFICATION_RETENTION_RLS_NOT_READY` (readiness gap, expect-ready iken) |
| `3` | Privacy guardrail ihlali (forbidden token response'ta) |
| `4` | Schema/shape mismatch (eksik key, hatalı tip) |

Ham API response body CI log'una yazılmaz.

### CI (Faz 108 / Faz 109)

- Fixture: `test-content-notification-retention-smoke-fixtures.sh` in job `retention-smoke-fixtures`.
- Live staging: `retention-staging-smoke` (opt-in). Workflow: [`.github/workflows/retention-readiness.yml`](../.github/workflows/retention-readiness.yml).
- Faz 109: GitHub job summary + artifact `retention-staging-smoke-evidence` — notification domain satırı sanitized status/exit/message içerir.
- Optional SQL: `check-notification-retention-rls-readiness.sql` (manual `psql`).

### Dedicated retention datasource (Faz 110)

Helm `retentionDatasource.notification` → `NOTIFICATION_RETENTION_DATASOURCE_*` (Faz 111: dedicated pool for platform retention counts when enabled). Staging rollout: [`staging-dedicated-retention-e2e-checklist.md`](../scripts/retention/staging-dedicated-retention-e2e-checklist.md). **Faz 113:** actuator `notificationRetentionDataSourceHealth` (safe diagnostics, no JDBC in JSON). **Faz 114:** staging CR evidence — [`retention-staging-e2e-evidence-formats.md`](../scripts/retention/retention-staging-e2e-evidence-formats.md). See [`retention-datasource-ops-handoff.md`](retention-datasource-ops-handoff.md).

Production enable için `EXPECT_NOTIFICATION_RETENTION_READY=true` ile exit 0 zorunlu.

## 10. Rollback / Disable

Sorun durumunda hızlı geri dönüş GitOps üzerinden:

```yaml
config:
  notificationRetentionDryRunCountsEnabled: "false"
  notificationRetentionIntegrationEnabled: "false"
```

`NOTIFICATION_RETENTION_DRY_RUN_COUNTS_ENABLED=false` notification-service plan endpoint'ini boş target listesi + `NOTIFICATION_RETENTION_DRY_RUN_DISABLED` warning'iyle döndürür. `NOTIFICATION_RETENTION_INTEGRATION_ENABLED=false` gateway'in notification plan merge yapmasını durdurur; identity + content planı kendi başına döner. `/admin/retention/platform/targets` etkilenmez.

DB role taraflı problemler için:

- Retention role parolasını secret manager üzerinden döndür.
- Geçici olarak retention datasource override'ını kaldır.
- Connection pool retention bağlantısını drop eder; tenant runtime path'leri ve Faz 83 worker etkilenmez.

## 11. Production Rollout Checklist

Tüm madde "yes" olmadan production enable yapılmaz:

- [ ] Dedicated retention DB role oluşturuldu ve `notebook_notification_retention` capability'leri `pg_roles`'ta doğrulandı.
- [ ] `GRANT SELECT` privilege'ları 5 tablo bazında verildi; yazma izni yok.
- [ ] Preflight SQL bölümleri 1-6 beklenen sonuçları döndürdü.
- [ ] Service JWT trust config gateway signing kid ile rotate edildi.
- [ ] Observability: `notification_retention_dry_run_total{result}` ve `notification_retention_count_duration_seconds` dashboard'a eklendi; `result="error"` üzerinde alert hazır.
- [ ] Rollback toggle (`notificationRetentionDryRunCountsEnabled`, `notificationRetentionIntegrationEnabled`) GitOps PR'ı pre-merge hazır.
- [ ] Change-request approve edildi (governance flow).
- [ ] Smoke script production'a karşı `EXPECT_NOTIFICATION_RETENTION_READY=true` ile exit 0 döndü.

Checklist eksikse rollout reddedilir; ayrı bir PR ile tekrar denenir. Prod default `false` korunur.

## 12. Güvenlik ve PII Sınırları

- Response/log/metric içinde notification body, raw payload, email subject/body, recipient email, userId/email, workspaceId yer almaz (Faz 102 garanti; JSON serialization testi ile doğrulanır).
- Backend SQL exception raw mesajı response'a sızmaz. `NotificationPlatformRetentionPlanService.classifyDbFailure` `PermissionDeniedDataAccessException` / SQLState `42501` durumunu `NOTIFICATION_RETENTION_DB_PERMISSION_DENIED` symbolic koduna map eder.
- `NOTIFICATION_RETENTION_RLS_NOT_READY` gelecek bir readiness probe için ayrılmış symbolic reserve'dir; backend aktif üretmez, smoke script readiness gap sinyali olarak yorumlar.
- Audit metadata target key, count'lar, capped flag, warning count ve error class simpleName + symbolic warning code içerir. Raw SQLState veya raw message audit'e yazılmaz.
- Metrics label cardinality registry target key ile sınırlı; `userId`/`email`/`recipient`/`workspaceId` label değildir.
- Retention role parolası secret manager dışında commit edilmez; runbook/script secret içermez.
- Production enable ayrı GitOps PR ve governance change-request onayı gerektirir.
