# Faz 101: Retention RLS Production Runbook + Guardrails

## Hedef

Faz 99–100 ile gelen platform/content retention dry-run count akışlarının production RLS ortamında güvenli çalışabilmesi için runbook, config guardrail, smoke/integration doğrulama ve dokümantasyon hazırlamak.

Bu fazda yeni destructive purge eklenmeyecek. Amaç, cross-workspace aggregate retention count query'lerinin production'da hangi DB role, service JWT, config ve operasyon adımlarıyla güvenli çalışacağını netleştirmektir.

## Mevcut Durum

Faz 99'da content-service aggregate-only retention dry-run endpoint'i eklendi. Gateway platform retention planı content-service count sonuçlarını merge edebiliyor.

Faz 100'de identity-service `RetentionTargetRegistry` content target status'ları Faz 99 ile hizalandı. Dev/staging GitOps values içinde content retention dry-run + integration enable edildi; prod açıkça disabled kaldı.

Kalan önemli açık: Retention count query'leri cross-workspace aggregate gerektirir. Production'da RLS aktifken bu query'lerin güvenli çalışması için BYPASSRLS role, dedicated retention DB user veya `row_security=off` stratejisi netleştirilmelidir.

## Scope İçi

- Production RLS retention runbook yazmak.
- Dedicated retention DB role stratejisini dokümante etmek.
- Content retention dry-run için production guardrail configlerini netleştirmek.
- DB role preflight SQL script eklemek.
- Retention RLS smoke script veya ops doğrulama script'i eklemek.
- `docs/platform-retention-governance.md` ve production readiness dokümanlarını güncellemek.
- Existing tests/runbooks ile uyumlu minimal backend guardrail eklemek.
- Prod config'in default disabled kalmasını doğrulamak.
- Dev/staging rollout notlarını netleştirmek.

## Scope Dışı

- Destructive purge.
- Content delete.
- Audit purge.
- Object storage lifecycle.
- Tenant-specific retention policy.
- eDiscovery export.
- Yeni microservice.
- RLS modelini tüm servisler için yeniden tasarlamak.
- Production secret oluşturmak veya gerçek credential yazmak.

## Production RLS Problem Statement

Retention dry-run count query'leri workspace-scoped normal kullanıcı query'lerinden farklıdır:

- Cross-workspace aggregate count üretir.
- Note/comment body okumaz.
- Sadece bounded target count ve cutoff timestamp kullanır.
- Admin/ops amaçlıdır.
- Response aggregate-only kalır.

Bu nedenle production'da normal tenant-scoped DB role ile çalışması beklenmez. Dedicated retention admin DB role veya DBA-controlled bypass stratejisi gerekir.

## Runbook İçeriği

Yeni doküman:

- `docs/retention-rls-production-runbook.md`

İçermeli:

1. Amaç ve kapsam
2. Neden normal RLS tenant context yeterli değil?
3. Dedicated retention DB role önerisi
4. BYPASSRLS / `row_security=off` yaklaşımı
5. Minimum DB privilege set'i
6. Kullanılacak env/config değerleri
7. Service JWT trust gereksinimleri
8. Preflight SQL doğrulama
9. Smoke test adımları
10. Rollback / disable adımları
11. Güvenlik ve PII sınırları
12. Prod rollout checklist

## DB Role / SQL Script

Yeni script önerisi:

- `scripts/retention/check-retention-rls-readiness.sql`

Kontrol edeceği konular:

- Current DB user
- `row_security` durumu
- BYPASSRLS capability, varsa
- `note_versions` count query çalışıyor mu?
- `comments` count query çalışıyor mu?
- Index varlığı:
  - `idx_note_versions_created_at`
  - `idx_comments_created_at`
- Query sadece count döndürüyor mu?

Script destructive işlem yapmamalı.

## Smoke Script

Ops script önerisi:

- `scripts/retention/content-retention-dry-run-smoke.sh`

Kontrol:

- Gateway `/admin/retention/platform/plan` çağrısı
- Content target status:
  - `content.note_versions`
  - `content.comments`
  - `content.search_documents`
- `DRY_RUN_READY` görünürlüğü
- `eligibleCount` alanı numeric veya null-safe
- Response raw content içermiyor
- `CONTENT_RETENTION_SERVICE_UNAVAILABLE` varsa anlaşılır hata

Bu script secret içermemeli; token/env değerleri dışarıdan alınmalı.

## Backend Guardrails

Minimal backend iyileştirmeleri yapılabilir:

- Content retention plan response warning alanına RLS/readiness kaynaklı safe warning eklenebilir.
- Eğer DB permission hatası alınırsa:
  - `CONTENT_RETENTION_RLS_NOT_READY`
  - `CONTENT_RETENTION_DB_PERMISSION_DENIED`
- Raw SQL error response'a sızmamalı.
- Audit metadata sadece symbolic error code içermeli.

## Config

Prod default disabled kalmalı:

```properties
CONTENT_RETENTION_DRY_RUN_ENABLED=false
CONTENT_RETENTION_INTEGRATION_ENABLED=false
```

Dev/staging:

```properties
CONTENT_RETENTION_DRY_RUN_ENABLED=true
CONTENT_RETENTION_INTEGRATION_ENABLED=true
```

Runbook'ta production enable için şu önkoşullar yazılmalı:

- Dedicated DB role hazır.
- Service JWT trust config hazır.
- Preflight SQL geçti.
- Smoke script geçti.
- Observability dashboard/alert hazır.
- Rollback env toggle hazır.

## Security / Privacy

- Runbook ve scriptler secret içermez.
- SQL script destructive işlem yapmaz.
- Response/log/metric içinde note title, note body, contentBlocks, comment body, user email yok.
- DB permission error raw stack trace olarak admin response'a dönmez.
- Production enable ayrı GitOps PR ve onay gerektirir.
- Prod default disabled korunur.

## Test Gereksinimleri

### Backend

- DB permission exception safe warning'e map edilir.
- Raw SQL exception response'a sızmaz.
- Existing content retention tests bozulmaz.
- Existing gateway platform retention tests bozulmaz.

### Scripts

- `scripts/retention/check-retention-rls-readiness.sql` syntax olarak geçerli.
- `scripts/retention/content-retention-dry-run-smoke.sh` shellcheck uyumlu veya mevcut script stiline uygun.
- Scriptler secret hardcode etmez.

### Docs

- Production readiness doc retention RLS runbook'a link verir.
- Platform retention governance doc Faz 101 RLS readiness bölümünü içerir.
- README veya ops docs gerekli linkleri içerir.

## Kabul Kriterleri

- `docs/retention-rls-production-runbook.md` eklendi.
- RLS readiness SQL script eklendi.
- Content retention dry-run smoke script eklendi.
- DB permission/RLS failure için safe warning/error mapping dokümante edildi veya uygulandı.
- Prod default disabled kaldı.
- Dev/staging enable davranışı dokümante edildi.
- Hiçbir destructive işlem eklenmedi.
- Docs güncellendi.
- Hedefli backend/script doğrulamaları çalıştırıldı veya environment sebebiyle çalıştırılamayanlar açık raporlandı.
