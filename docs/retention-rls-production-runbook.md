# Retention RLS Production Runbook (Faz 101)

Faz 99'da content-service'e eklenen aggregate-only retention dry-run count akışı (`GET /internal/admin/retention/content/plan`) cross-workspace bir aggregate sorgu üretir. Production'da RLS aktifken bu akış standart tenant-scoped runtime role ile çalışmaz; bu runbook hangi DB role, env, preflight ve smoke adımlarıyla güvenle production'a açılacağını tanımlar.

Bu runbook destructive bir aksiyon önermez. Content delete, audit purge, object storage lifecycle ve eDiscovery export kapsam dışıdır.

> **See also:**
> - notification-service: [`notification-retention-rls-production-runbook.md`](notification-retention-rls-production-runbook.md) (Faz 103; role `notebook_notification_retention`)
> - workspace-service: [`workspace-retention-rls-production-runbook.md`](workspace-retention-rls-production-runbook.md) (Faz 107; role `notebook_workspace_retention`)
> - search-service: [`search-retention-rls-production-runbook.md`](search-retention-rls-production-runbook.md) (Faz 107; role `notebook_search_retention`)

## 1. Amaç ve Kapsam

- Kapsam: content-service `note_versions`, `comments`, `search_index_outbox` üzerinde aggregate-only count.
- Amaç: production'da retention dry-run akışını yanlış konfigürasyon nedeniyle yanıltıcı sıfır count, raw SQL hatası veya cross-tenant kaçak olmadan çalıştırmak.
- Out of scope: destructive purge, archive-before-delete, per-tenant retention policy, RLS modelinin yeniden tasarlanması.

İlgili docs:

- [`platform-retention-governance.md`](platform-retention-governance.md)
- [`database-roles-and-rls.md`](database-roles-and-rls.md)
- [`internal-service-auth.md`](internal-service-auth.md)
- [`runtime-rls-rollout.md`](runtime-rls-rollout.md)

## 2. Neden Normal RLS Tenant Context Yeterli Değil?

Retention count'ları platform-wide aggregate üretir. Workspace policy'leri `app.current_workspace_id` set edilmemiş bir oturumda satırları silently filtreler ve aggregate count sıfır döner. Yani normal tenant role ile çalıştırılırsa:

- Plan response yanıltıcı 0 count gösterir.
- Operatör retention politikasının tetiklendiğini sanır.
- Audit'te yanlış "no eligible rows" izi kalır.

Sonuç: retention bu yüzden ayrı, BYPASSRLS'li ya da `row_security=off` ile çalışan bir bağlantı gerektirir.

## 3. Dedicated Retention DB Role Önerisi

Önerilen rol: `notebook_content_retention`.

Karakteristikler:

- `rolcanlogin = true`
- `rolbypassrls = true` *veya* runtime'da `SET row_security TO off` yetkisi
- `rolsuper = false`
- `rolcreatedb = false`, `rolcreaterole = false`
- Sadece `SELECT` privilege; `INSERT/UPDATE/DELETE` verilmemeli.

Migration owner veya superuser ile setup örneği (gerçek parola placeholder; secret manager'dan verilir):

```sql
CREATE ROLE notebook_content_retention LOGIN PASSWORD '<replace-with-secret>' BYPASSRLS;
GRANT CONNECT ON DATABASE notebook_platform TO notebook_content_retention;
GRANT USAGE ON SCHEMA public TO notebook_content_retention;
GRANT SELECT ON note_versions, comments, search_index_outbox TO notebook_content_retention;
```

`BYPASSRLS` istenmiyorsa alternatif: aynı role'e sadece `SELECT` GRANT'leri verilir ve connection string'inde `options=-c row_security=off` ile bağlanılır. Bu yaklaşım yalnız retention bağlantısı için kullanılmalıdır; başka servis bu connection'ı paylaşmamalıdır.

## 4. BYPASSRLS vs `row_security=off` Trade-off

| Yöntem | Avantaj | Risk |
|---|---|---|
| `BYPASSRLS` role | Tek nokta config, role-bound | Role kazara başka servise verilirse tüm RLS atlatılır; rotasyon dikkat ister |
| `row_security=off` connection option | Role flag değişmez; yalnız bağlantı opt-out | Connection pool config gerekiyor; yanlış konfigde sessizce default'a döner |

Karar kriterleri:

- DBA tek rol prensibi tercih ediyor → BYPASSRLS.
- Production rolleri minimum capability ile sınırlanmalı → `row_security=off` connection option ve plain SELECT GRANT.

Her iki yöntem de yalnızca retention path'i için kullanılmalıdır; başka servis paylaşmamalıdır.

## 5. Minimum DB Privilege Set

| Object | Privilege |
|---|---|
| `notebook_platform` DB | `CONNECT` |
| `public` schema | `USAGE` |
| `note_versions` | `SELECT` |
| `comments` | `SELECT` |
| `search_index_outbox` | `SELECT` |

Diğer tüm tablo / object'ler için privilege verilmemeli. `INSERT/UPDATE/DELETE/TRUNCATE/ALTER` yetkisi yok.

## 6. Env / Config

Content-service ve gateway tarafında Faz 99 env'leri (yeni değil, hatırlatma):

| Env | Default | Production değeri |
|---|---|---|
| `CONTENT_RETENTION_DRY_RUN_ENABLED` | `false` | `false` (rollout sonrası `true`) |
| `CONTENT_RETENTION_INTEGRATION_ENABLED` (gateway) | `false` | `false` (rollout sonrası `true`) |
| `CONTENT_RETENTION_INTERNAL_URL` | `http://content-service:8083` | cluster URL |
| `CONTENT_RETENTION_INTERNAL_TIMEOUT_MS` | `3000` | env'e göre, default kabul edilebilir |
| `CONTENT_RETENTION_MAX_COUNT_QUERY_LIMIT` | `100000` | env'e göre |
| `CONTENT_RETENTION_*_RETENTION_DAYS` | 365 / 365 / 90 | policy'ye göre |
| `CONTENT_RETENTION_ADMIN_SERVICE_JWT_KID` | `""` | rotation ile set |
| `CONTENT_RETENTION_ADMIN_SERVICE_JWT_PUBLIC_KEY` / `_PATH` | `""` | rotation ile set |

Retention'a özel runtime DB connection (önerilen, Faz 99 datasource bind etmeden ayrı):

- Connection URL retention role credential'ını kullanır.
- `row_security=off` opsiyonu seçildiyse JDBC URL'ine `?options=-c%20row_security%3Doff` eklenir veya `application.yml` üzerinden Hikari `dataSourceProperties` ile geçilir.
- Bu connection sadece retention count path'inde kullanılır; ana content-service runtime connection'ı RLS-aware tenant role ile devam eder.

Not: Faz 101 backend kodu ayrı datasource binding eklemez. Mevcut datasource retention path'i için kullanılır; production'da retention role'e geçişin nasıl yapılacağı DBA + platform engineering tarafından datasource override veya pgbouncer/connection routing ile çözülür.

## 7. Service JWT Trust Gereksinimleri

Faz 99 ekibinden gelen `internal:admin:retention:read` scope'lu service JWT zorunluluğu korunur. Production'a alma için:

- `CONTENT_RETENTION_ADMIN_SERVICE_JWT_KID`, `_PUBLIC_KEY` veya `_PUBLIC_KEY_PATH` gateway signing kid ile coordinated rotate edilir.
- Rotasyon prosedürü için bkz. [`internal-service-auth.md`](internal-service-auth.md) ve [`admin-rbac-override-reload.md`](admin-rbac-override-reload.md).
- Gateway side `gateway.admin.platform-retention.content.enabled=true` yapıldığında signing key gateway'in mevcut service JWT signer'ından gelir.

## 8. Preflight SQL Doğrulama

Script: [`scripts/retention/check-retention-rls-readiness.sql`](../scripts/retention/check-retention-rls-readiness.sql)

Çalıştırma (migration owner veya privileged inspection role ile):

```bash
psql "$DB_MIGRATION_URL" \
  -v retention_role=notebook_content_retention \
  -f scripts/retention/check-retention-rls-readiness.sql
```

Beklenen sonuçlar:

- Section 1: `rolname=notebook_content_retention`, `bypasses_rls=true` (veya `row_security=off` yaklaşımı seçildiyse `false` + connection option). `is_superuser=false`.
- Section 3: yalnız `can_select=true`; `can_insert/update/delete=false`.
- Section 4: `idx_note_versions_created_at` ve `idx_comments_created_at` listede görünür.
- Section 5: RLS hedef tablolarda `rls_enabled=true` ise retention role BYPASSRLS olmalı veya bağlantı `row_security=off` ile gelmeli.
- Section 6: probe count permission_denied vermez; sıfır row da kabul (boş tablo).

Herhangi bir bölüm beklenmeyen sonuç verirse production rollout başlatılmamalıdır.

## 9. Smoke Test Adımları

Script: [`scripts/retention/content-retention-dry-run-smoke.sh`](../scripts/retention/content-retention-dry-run-smoke.sh)

Çalıştırma:

```bash
export API_BASE_URL=https://api.example.com
export ADMIN_ACCESS_TOKEN='<admin JWT with admin:retention:read>'
export EXPECT_CONTENT_RETENTION_READY=true
bash scripts/retention/content-retention-dry-run-smoke.sh
```

Script:

- `/admin/retention/platform/plan?dryRun=true` çağırır.
- Content target'ların (`content.note_versions`, `content.comments`, `content.search_documents`) plan'da göründüğünü doğrular.
- `eligibleCount`, `purgeableCount` shape'lerini kontrol eder.
- Response içinde `contentBlocks`, `noteBody`, `commentBody`, `userEmail`, `noteTitle` substring'lerinin geçmediğini doğrular (exit `3` privacy).
- Readiness gap warnings (`CONTENT_RETENTION_SERVICE_UNAVAILABLE`, `CONTENT_RETENTION_DRY_RUN_DISABLED`, `CONTENT_RETENTION_DB_PERMISSION_DENIED`, `CONTENT_RETENTION_RLS_NOT_READY`) → exit `2` when `EXPECT_CONTENT_RETENTION_READY=true`.

Exit codes: `0` ok / expected gap; `2` readiness; `3` privacy; `4` shape. Ham response body loglanmaz.

### CI (Faz 108 / Faz 109)

- Fixture (no secret): `test-content-notification-retention-smoke-fixtures.sh` via workflow job `retention-smoke-fixtures`.
- Live staging: `retention-staging-smoke` job (opt-in; secrets `RETENTION_STAGING_API_BASE_URL`, `RETENTION_STAGING_ADMIN_ACCESS_TOKEN`). See [`.github/workflows/retention-readiness.yml`](../.github/workflows/retention-readiness.yml).
- **Faz 109 evidence:** job summary tablosu + artifact `retention-staging-smoke-evidence` (sanitized JSON/Markdown). Content satırı status: `passed` / `expected-gap` / `readiness-gap` / `privacy-failure` / `shape-failure` / `skipped`.
- Optional SQL preflight: `check-retention-rls-readiness.sql` (manual `psql`, not in CI).

## 10. Rollback / Disable

Sorun durumunda hızlı geri dönüş GitOps üzerinden:

```yaml
config:
  contentRetentionDryRunEnabled: "false"
  contentRetentionIntegrationEnabled: "false"
```

`CONTENT_RETENTION_DRY_RUN_ENABLED=false` content-service plan endpoint'ini boş target listesi + `CONTENT_RETENTION_DRY_RUN_DISABLED` warning'iyle döndürür. `CONTENT_RETENTION_INTEGRATION_ENABLED=false` gateway'in content plan merge yapmasını durdurur; identity plan kendi başına döner.

DB role taraflı problemler için:

- Retention role parolasını secret manager üzerinden döndür.
- Geçici olarak retention datasource override'ını kaldır.
- Connection pool retention bağlantısını drop eder; tenant runtime path'leri etkilenmez.

## 11. Güvenlik ve PII Sınırları

- Response/log/metric içinde note title, note body, contentBlocks, comment body, user email yer almaz (Faz 99 garanti).
- Backend SQL exception raw mesajı response'a sızmaz. Faz 101 `ContentRetentionPlanService.classifyDbFailure` ile sadece symbolic warning code döner.
- Audit metadata target key, count'lar, capped flag, warning count ve error class simpleName + symbolic warning code içerir. Raw SQLState veya raw message audit'e yazılmaz.
- Metrics label cardinality registry target key ile sınırlı.
- Retention role parolası secret manager dışında commit edilmez.

## 12. Production Rollout Checklist

Tüm madde "yes" olmadan production enable yapılmaz:

- [ ] Dedicated retention DB role oluşturuldu ve `notebook_content_retention` capability'leri `pg_roles`'ta doğrulandı.
- [ ] `GRANT SELECT` privilege'ları tablo bazında verildi; yazma izni yok.
- [ ] Preflight SQL bölümleri 1-6 beklenen sonuçları döndürdü.
- [ ] Service JWT trust config (`CONTENT_RETENTION_ADMIN_*`) gateway signing kid ile rotate edildi.
- [ ] Observability: `content_retention_dry_run_total{result}` ve `content_retention_count_duration_seconds` dashboard'a eklendi; `content_retention_dry_run_total{result="error"}` üzerinde alert hazır.
- [ ] Rollback toggle (`CONTENT_RETENTION_DRY_RUN_ENABLED`, `CONTENT_RETENTION_INTEGRATION_ENABLED`) GitOps PR'ı pre-merge hazır.
- [ ] Change-request approve edildi (governance flow).
- [ ] Smoke script production'a karşı exit 0 döndü.

Checklist eksikse rollout reddedilir; ayrı bir PR ile tekrar denenir.
