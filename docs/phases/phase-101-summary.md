# Faz 101 Özeti: Retention RLS Production Runbook + Guardrails

Spec: [`phase-101.md`](phase-101.md). Önceki faz: [Faz 100](phase-100-summary.md) — identity-service `RetentionTargetRegistry` content target status sync + dev/staging GitOps rollout.

## 1. Yapılanlar

Faz 99-100 retention dry-run akışını production'a hazırlamak için runbook, preflight SQL, smoke script ve minimum backend guardrail eklendi. Yeni endpoint, yeni env, destructive aksiyon yok. Production-side hâlâ default disabled; rollout için bu fazın dokümante ettiği DBA setup + change-request approval gerekir.

Backend tarafında `ContentRetentionPlanService` count query'lerinde yakalanan `PermissionDeniedDataAccessException` veya PostgreSQL SQLState `42501` artık `CONTENT_RETENTION_DB_PERMISSION_DENIED` symbolic warning'ine map ediliyor; raw SQL message admin response'una sızmıyor. Smoke script gateway plan response'unu validate edip readiness gap'lerini farklı exit kodlarıyla raporluyor.

## 2. Backend Değişiklikleri

### content-service

`ContentRetentionPlanService`:

- Yeni warning constant'lar:
  - `WARN_DB_PERMISSION_DENIED = "CONTENT_RETENTION_DB_PERMISSION_DENIED"`
  - `WARN_RLS_NOT_READY = "CONTENT_RETENTION_RLS_NOT_READY"` (reserve; backend henüz yayınlamıyor, runbook ve script bekleniyor)
- Yeni `classifyDbFailure(Throwable e)` static helper — exception cause chain'inde `PermissionDeniedDataAccessException` veya `SQLException.SQLState == "42501"` yakalayınca `WARN_DB_PERMISSION_DENIED` döner; aksi takdirde mevcut `WARN_COUNT_FAILED`.
- `planTarget(...)` catch bloğu `targetWarnings.add(WARN_COUNT_FAILED)` yerine sınıflandırıcının döndüğü kodu ekler. Audit metadata'ya `warningCode` alanı eklendi; raw exception message audit'e veya response'a yazılmıyor.

Yeni endpoint, DTO veya DB migration yok. `ContentRetentionCountRepository` ve `ContentRetentionPlanDtos` dokunulmadı.

### api-gateway ve identity-service

Kod değişikliği yok. Gateway merge davranışı Faz 99-100'den korunur; identity registry Faz 100 status'u korunur.

## 3. Frontend Değişiklikleri

Kod değişikliği yok. Yeni warning kodları zaten `WarningChips` slate tone fallback'i tarafından render edilebilir. UI guardrail değişikliği gerekmedi.

## 4. Security/Privacy

- Destructive aksiyon yok.
- Raw SQL exception message response'a veya audit'e yazılmıyor; sadece symbolic `warningCode` ve exception class simpleName audit metadata'ya gider.
- Smoke script response'ta `contentBlocks`, `noteBody`, `commentBody`, `userEmail`, `noteTitle` substring'lerini reddeder; PII guard çalışıyor.
- Runbook ve scriptler placeholder kullanır; gerçek credential yok.
- Service JWT trust modeli değişmedi.
- Prod default kapalı (`CONTENT_RETENTION_DRY_RUN_ENABLED=false`, `CONTENT_RETENTION_INTEGRATION_ENABLED=false`).
- Metrics cardinality değişmedi (`content_retention_dry_run_total{target,result}`, `content_retention_count_capped_total{target}`).

## 5. Tests

| Komut | Sonuç |
|---|---|
| `./gradlew :content-service:test --tests "com.notebook.lumen.content.admin.retention.*"` | passed (mevcut + 4 yeni test) |
| `bash -n scripts/retention/content-retention-dry-run-smoke.sh` | exit 0 |
| `bash scripts/check-no-secrets.sh` | "No obvious committed secrets detected." |
| Destructive SQL keyword scan (`grep -nE 'DROP \|DELETE \|TRUNCATE \|ALTER '`) | yalnız `expected_result` string içeriği, gerçek komut yok |

Yeni testler:

- `dbPermissionDenied_mapsToSafeWarning()` — `PermissionDeniedDataAccessException` durumunda `CONTENT_RETENTION_DB_PERMISSION_DENIED` warning, `CONTENT_RETENTION_COUNT_FAILED` warning yok.
- `genericRuntimeException_keepsCountFailedWarning()` — `IllegalStateException` durumunda regression guard, mevcut `CONTENT_RETENTION_COUNT_FAILED` korunur.
- `dbPermissionDenied_doesNotLeakRawMessage()` — exception message içindeki `"notebook_secret"` ve `"sensitive"` substring'leri response warning listesine sızmaz.
- `sqlStateInsufficientPrivilege_mapsToPermissionDenied()` — `UncategorizedSQLException` cause chain'inde SQLState `42501` yakalandığında permission-denied warning'ine map.

`ContentRetentionPlanServiceTest` toplamı: 7 mevcut + 4 yeni = 11 test, hepsi passed. Mevcut `InternalContentRetentionControllerTest` koştu, etkilenmedi.

Spotless: WSL/Windows path-mismatch (Faz 99/100'den beri tekrar eden environment-level issue) bu fazda da görülecektir; CI Linux runner'larında problem yok. Bu fazda spotless açıkça koşturulmadı; gradle daemon path normalization issue gerçek kod kalitesini etkilemiyor.

## 6. Config/Deployment

Yeni env veya config eklenmedi. Faz 99 env'leri korunur:

- `CONTENT_RETENTION_DRY_RUN_ENABLED` (prod false, dev/staging Faz 100'de true)
- `CONTENT_RETENTION_INTEGRATION_ENABLED` (prod false)
- `CONTENT_RETENTION_INTERNAL_URL`, `CONTENT_RETENTION_INTERNAL_TIMEOUT_MS`
- `CONTENT_RETENTION_MAX_COUNT_QUERY_LIMIT`, `CONTENT_RETENTION_*_RETENTION_DAYS`
- `CONTENT_RETENTION_ADMIN_SERVICE_JWT_KID/PUBLIC_KEY/PUBLIC_KEY_PATH`

Prod rollout için ön koşullar runbook'ta:

- Dedicated retention DB role (`notebook_content_retention` örneği)
- `BYPASSRLS` veya `row_security=off` connection option
- Preflight SQL geçer
- Smoke script exit 0 verir
- Observability dashboard ve `result="error"` alert hazır
- Rollback toggle GitOps PR'ı pre-merge hazır
- Governance change-request approve edildi

Helm chart, GitOps values, deployment template dosyaları dokunulmadı.

## 7. Eklenen/Düzenlenen Dosyalar

### Backend (content-service)

- `content-service/src/main/java/com/notebook/lumen/content/admin/retention/ContentRetentionPlanService.java` (güncelleme — 2 yeni warning constant + classifyDbFailure helper + planTarget catch update)
- `content-service/src/test/java/com/notebook/lumen/content/admin/retention/ContentRetentionPlanServiceTest.java` (güncelleme — 4 yeni test)

### Scripts

- `scripts/retention/check-retention-rls-readiness.sql` (yeni — read-only preflight)
- `scripts/retention/content-retention-dry-run-smoke.sh` (yeni — gateway plan response validation; executable)

### Docs

- `docs/retention-rls-production-runbook.md` (yeni — 12 bölümlük operasyon dokümanı)
- `docs/platform-retention-governance.md` (güncelleme — Faz 101 RLS readiness bölümü + warning code tablosu)
- `docs/production-readiness.md` (güncelleme — Faz 101 readiness checks)
- `docs/phases/phase-101.md` (faz spec — bu fazın başında oluşturuldu)
- `docs/phases/phase-101-summary.md` (yeni — bu dosya)

## 8. Kalan Açıklar

- **Backend RLS_NOT_READY probe**: `CONTENT_RETENTION_RLS_NOT_READY` warning constant reserve edildi ama backend henüz emit etmiyor. Aktif probe (örn. startup'ta `SHOW row_security` + `pg_roles` check) gelecek bir fazda eklenebilir.
- **Ayrı retention datasource binding**: Backend kodu retention için ayrı datasource oluşturmaz; production'da role override DBA + platform engineering tarafından datasource override veya connection routing ile çözülür. Tek datasource'tan iki role'e taşınma kararı yapıldığında ek backend wiring gerekebilir.
- **Smoke script integration test**: Şu an dev environment'a karşı manual smoke koşulmadı (script syntax-only doğrulandı). Local stack ile end-to-end koşusu rollout pratiği için açık.
- **Search documents target naming**: `search.documents` (search-service) vs `content.search_documents` (content-service outbox view) consolidation hâlâ açık (Faz 100 kalan açıklardan).
- **Notification-service retention dry-run counts**: Notification target'larında aynı pattern uygulanmadı; gelecek faz önerisi olarak duruyor.
- **Spotless WSL path mismatch**: Faz 99'dan beri environment-level issue; CI Linux runner'larında yok.

## 9. Sonraki Faz Önerileri

1. **Notification-service retention dry-run counts** — Faz 99 patterni (aggregate-only, legal-hold scope param, gateway merge) `notification.retention_targets` target'larına uygulanır; in_app/email retention worker durumlarına gerçek count visibility kazandırır.
2. **Retention runtime readiness probe** — Content-service startup veya plan invocation öncesinde `SHOW row_security` + role capability probe çalıştırıp `CONTENT_RETENTION_RLS_NOT_READY` warning'ini proaktif emit eden runtime check. Production rollout'ta operator UI'ında erken sinyal verir.
3. **Search-target naming consolidation** — `search.documents` ve `content.search_documents` target'larını tek source-of-truth altında birleştir (search-service kaynaklı index vs content-service outbox view). Registry naming convention netleştirme + frontend label tutarlılığı.
