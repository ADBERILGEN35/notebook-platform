# Notebook Platform

## Genel Bakış

Engineering takımları için block-based not tutma platformunun MVP iskeleti. Multi-tenant (workspace-scoped) SaaS — kullanıcılar workspace altında notebook ve note oluşturur, BlockNote editör ile yazar; SCIM/SSO ile enterprise identity, fine-grained admin RBAC, audit/legal-hold/retention governance, SSE+Redis fanout realtime notification, PWA offline read mode ve semantic merge desteklenir.

Stack: Java 25 + Spring Boot 4.0.6 (Gradle Kotlin DSL multi-project) backend, PostgreSQL 16 + Redis 7 + Flyway, React 19 + Vite + TypeScript + BlockNote frontend, Docker Compose / Helm / GitOps (ArgoCD), OTLP tracing, Cosign/Syft/Trivy supply-chain.

## Repo Yapısı

```
api-gateway/          Spring Cloud Gateway; public auth + protected routing, header sanitization, JWKS JWT verify
identity-service/     User, JWT key rotation, refresh token, SSO/SCIM, MFA/WebAuthn, break-glass, audit/SIEM outbox
workspace-service/    Workspace, notebook, tag, invitation, membership; internal permission contract
content-service/      Note, version, comment, search outbox, semantic merge analyze/apply, note revision
notification-service/ Email/in-app, SSE realtime, durable DB outbox, digest, retention/legal-hold
search-service/       PostgreSQL FTS provider-agnostic search; outbox-driven indexing, reindex/backfill
common-security/      Shared service-JWT signing/verification (boot jar disabled)
frontend/             React + Vite SPA; BlockNote editor, admin shell, Notification Center, PWA shell
deploy/               helm/ chart + gitops/ Argo CD env values + policies/ admission examples
docs/                 Konu-bazlı tasarım/runbook dokümanları
docs/phases/          Numaralı faz raporları (phase-N.md)
scripts/              Smoke, RLS, SBOM/Trivy, audit/email/notification ops
observability/        Grafana dashboards + Prometheus rules
```

Java paket: `com.notebook.lumen.<service>.<layer>` — layers: `api / audit / client / config / domain / dto / mapper / repository / service / shared / tenant` (+`admin` content-service'te).

## Çalışma Komutları

| İş | Komut |
|---|---|
| Bağımlılıkları başlat | `docker compose -f docker-compose.dev.yml up -d` |
| Tüm servisleri çalıştır | `./gradlew bootRun` |
| Tek servis | `./gradlew :<service>:bootRun` |
| Tek servis test | `./gradlew :<service>:test` |
| Test filtresi | `./gradlew :<service>:test --tests "<glob>"` |
| Tüm backend check | `./gradlew check` |
| RLS integration test | `./gradlew rlsIntegrationTest` (Docker gerekir) |
| Format kontrol/uygula | `./gradlew spotlessCheck` / `spotlessApply` |
| Frontend dev | `cd frontend && npm run dev` |
| Frontend birim test | `cd frontend && npm test` |
| Frontend type check | `cd frontend && npx tsc -b` |
| Frontend e2e | `cd frontend && npm run test:e2e` |
| Frontend lint | `cd frontend && npm run lint` |
| Smoke testler | `bash scripts/smoke-test*.sh` |
| Helm template doğrulama | `bash scripts/helm-template-check.sh` |
| Secret commit kontrolü | `bash scripts/check-no-secrets.sh` |

Servis portları: api-gateway 8080, identity 8081, workspace 8082, content 8083, notification 8084, search 8085.

## Tech Stack & Konvansiyonlar

**Backend:**
- Java 25 toolchain (Gradle auto-download), Spring Boot 4.0.6, JUnit 5, Testcontainers
- Spotless + google-java-format 1.28.0 (4-space indent, LF EOL); CI'da `spotlessCheck` blocking
- Flyway migration: `V{n}__{snake_case_name}.sql` — yeni migration daima yeni numara
- Katmanlı paket ayrımı: cross-service çağrı `client/` altında HTTP client + service JWT
- Internal contract endpoint'leri `/internal/**` — gateway'e route edilmez; sadece service JWT ile çağrılır
- Error response standardı: `docs/error-codes.md`
- Log: JSON structured + `X-Request-Id` correlation; PII log'a yazılmaz

**Frontend:**
- React 19, Vite 8, TypeScript ~6.0, React Query, Zustand, React Router 7, Tailwind 4
- Editor: BlockNote `@blocknote/{core,mantine,react}`
- Feature klasörleri: `frontend/src/features/{admin,auth,comments,notebooks,notes,notifications,offline,search,versions,workspaces}`
- Shared: `frontend/src/shared/`, sayfa kompozisyonu `frontend/src/pages/`
- Test: vitest + `@testing-library/react`; e2e: Playwright

**Naming:**
- Java: `PascalCase` class, `camelCase` method/field, `UPPER_SNAKE` enum/const
- TS/TSX: dosyalar `kebab-case` (utility) ya da `PascalCase.tsx` (component); hook `useXxx`, store `useXxxStore`
- Migration: `V{n}__short_snake_case.sql`
- Doc: `kebab-case.md`; faz raporu `phase-{N}.md`

## Faz Çalışma Disiplini

- Yeni faz başlarken `docs/phases/phase-{N}.md` oluştur ve aşağıdaki 9 başlığı doldur.
- Detay teknik tasarım veya runbook gerekirse `docs/<konu>.md` ayrı dosya — phase-N.md o dosyaya link verir.
- Faz numarası `docs/phases/` adından sürdürülür; commit mesajına `Faz {N}: ...` eklemek önerilir.
- Phase-N.md'de tarihsel açıklama yok — şu anki durum yazılır. Önceki faz özetine link ver.

## Standart Faz Sonu Rapor Formatı

`docs/phases/phase-{N}.md` aşağıdaki 9 başlıkla dolar:

1. **Yapılanlar** — özet 1-2 paragraf, motivasyon + sonuç
2. **Backend değişiklikleri** — servis bazlı (yeni endpoint, DB değişiklik, kontrat değişikliği)
3. **Frontend değişiklikleri** — feature/sayfa bazlı (route, store, query, component)
4. **Security/Privacy** — yetkilendirme, audit event, PII gözden geçirme, RLS etkisi
5. **Tests** — koşulan komutlar + sonuç (örn. `./gradlew :content-service:test → 142 passed`)
6. **Config/Deployment** — yeni env, Helm template, GitOps override, docker-compose ek
7. **Eklenen/düzenlenen dosyalar** — kategorize: backend / frontend / docs / deploy / scripts
8. **Kalan açıklar** — bilinen kısıt, TODO, gelecek faza taşınan
9. **Sonraki faz önerileri** — istendiğinde; en fazla 3 öneri, gerekçeli

## Mutlak Kurallar (Yapma)

- **Destructive purge / hard delete önerme** — retention ve legal-hold governance çerçevesinde default dry-run; gerekçe + onay olmadan destructive aksiyon yazma. Referans: `docs/retention-governance.md`, `docs/platform-retention-governance.md`.
- **Yeni microservice ekleme** — mevcut 6 servisi (api-gateway/identity/workspace/content/notification/search) genişlet; yeni servis ihtiyacı doğarsa önce sor.
- **Internal endpoint'i gateway'e açma** — `/internal/**` sadece service JWT ile çağrılır; gateway route config'ine ekleme.
- **Mevcut Flyway V<n> migration'ını değiştirme** — her yeni şema değişikliği `V{max+1}__<name>.sql`; tarihsel migration'a dokunma.
- **Client header'ına güvenme** — `X-User-Id`, `X-User-Email`, `X-User-Roles`, `X-Workspace-Role` client'tan geliyorsa gateway sanitize eder; servis tarafında "trusted" gibi davranma. Sadece gateway-inject olanlar güvenilir.
- **Spotless format hatası bırakma** — commit/PR öncesi `./gradlew spotlessApply`; CI'da `spotlessCheck` blocking. Hook'u `--no-verify` ile geçme.

## Security & Privacy Sabitleri

- **PII yok**: response, log, analytics — email, kullanıcı adı, note body raw asla. Audit aggregate-only; gerekli yerde redaction.
- **Admin endpoint**: service JWT + `platform_permissions` claim + sensitive aksiyonlarda MFA. RBAC override reload, change-request approve, break-glass MFA zorunlu. Referans: `docs/admin-rbac.md`, `docs/admin-permission-matrix.md`, `docs/admin-rbac-override-reload.md`.
- **Workspace tenant izolasyonu**: tüm content/workspace I/O `X-Workspace-Id` scope + PostgreSQL RLS `SET LOCAL app.current_workspace_id` transaction içinde. Cross-tenant veri kaçağına yol açan endpoint yazma. Referans: `docs/database-roles-and-rls.md`, `docs/runtime-rls-rollout.md`.
- **Audit event şart**: state-changing endpoint (create/update/delete/approve/revoke) `audit_events` tablosuna yazar. Referans: `docs/audit-events.md`.
- **Internal service auth**: production hedef `INTERNAL_AUTH_MODE=service-jwt` (RS256); `static-token` sadece geçiş/rollback. Referans: `docs/internal-service-auth.md`.
- **Retention dry-run aggregate-only**: Retention dry-run count endpoint'leri aggregate-only olmalı. Response/log/metric içinde note title, body, contentBlocks, comment body, user email, workspace/note/user id gibi sensitive veya high-cardinality alanlar yer alamaz.
- **Retention count query guardrail**: Dry-run count query'leri indexed alanlar ve cutoff timestamp üzerinden çalışmalı; full content JSON/body scan yapılmamalı.

## Çalışma Tercihleri

- **Plan mode**: yeni faz veya cross-service değişiklikten önce plan çıkar, onay bekle. Tek dosya bugfix'te gerek yok.
- **Output stili**: 1-2 cümle gerekçe + değişiklik listesi + koşulan test komutu/sonucu. Faz sonunda dolu rapor. "Tam dosya" çıktısı verme; diff yeter.
- **Belirsizlik varsa sor**, tahmin yürütme — özellikle faz scope'u, destructive aksiyon, yeni kontrat/endpoint, yeni env'de.
- **Test koşmadan "tamamlandı" deme** — aşağıdaki test gereklilik matrisi geçerli.
- **Keşif yerine spesifik path**: "İncele/bul/keşfet" tipi geniş emirler yerine `@path/to/file` ile verilen dosyaları oku. Belirsizlik varsa kullanıcıya path sor, tarama başlatma.

## Test Gereklilik

| Değişiklik | Zorunlu komutlar |
|---|---|
| Tek servis (örn. content-service) | `./gradlew :content-service:test` + `./gradlew spotlessApply` |
| Frontend | `cd frontend && npm test && npx tsc -b` (e2e Playwright opsiyonel) |
| RLS / tenant izolasyon | `./gradlew rlsIntegrationTest` (Docker gerekir) |
| Cross-service / büyük faz sonu | `./gradlew check` |

Test başarısızsa "tamamlandı" deme — root cause düzelt.

## Sık Kullanılan Path'ler

| Kısayol | Path |
|---|---|
| Identity-service kök | `@identity-service/src/main/java/com/notebook/lumen/identity` |
| Workspace-service kök | `@workspace-service/src/main/java/com/notebook/lumen/workspace` |
| Content-service kök | `@content-service/src/main/java/com/notebook/lumen/content` |
| Notification-service kök | `@notification-service/src/main/java/com/notebook/lumen/notification` |
| Search-service kök | `@search-service/src/main/java/com/notebook/lumen/search` |
| Api-gateway kök | `@api-gateway/src/main/java/com/notebook/lumen/gateway` |
| Common security | `@common-security/src/main/java/com/notebook/lumen/security` |
| Frontend src | `@frontend/src` |
| Frontend features | `@frontend/src/features` |
| Content migrations | `@content-service/src/main/resources/db/migration` |
| Workspace migrations | `@workspace-service/src/main/resources/db/migration` |
| Identity migrations | `@identity-service/src/main/resources/db/migration` |
| Helm chart | `@deploy/helm/notebook-platform` |
| GitOps env values | `@deploy/gitops/environments` |
| Konu docs | `@docs` |
| Faz raporları | `@docs/phases` |
| Smoke scriptleri | `@scripts` |

## Referans Dokümanlar (Sık Bakılanlar)

- Mimari giriş: `README.md`
- Security threat model: `docs/security-threat-model.md`
- Production readiness: `docs/production-readiness.md`
- Internal service auth: `docs/internal-service-auth.md`
- RBAC: `docs/admin-rbac.md`, `docs/admin-permission-matrix.md`
- Audit: `docs/audit-events.md`, `docs/audit-query-api.md`, `docs/audit-retention.md`
- RLS: `docs/database-roles-and-rls.md`, `docs/runtime-rls-rollout.md`
- Retention/legal-hold: `docs/retention-governance.md`, `docs/platform-retention-governance.md`
- Hata kodları: `docs/error-codes.md`
- Frontend MVP: `docs/frontend-mvp.md`, `docs/frontend-e2e.md`
- GitOps: `docs/gitops-deployment.md`
