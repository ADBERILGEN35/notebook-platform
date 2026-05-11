# Notebook Platform (MVP Skeleton)

Engineering takımları için block-based not tutma platformunun MVP iskeleti.

## Prerequisites

- Java 25 (Gradle toolchain otomatik indirir)
- Docker / Docker Compose

## Local run

Önce bağımlılıkları ayağa kaldırın:

```bash
docker compose -f docker-compose.dev.yml up -d
```

Servisleri başlatın (root’tan hepsi):

```bash
./gradlew bootRun
```

Sağlık kontrolü endpoint’i:

- `http://localhost:8080/actuator/health` (api-gateway)
- `http://localhost:8081/api/ok` (identity-service)
- `http://localhost:8082/api/ok` (workspace-service)
- `http://localhost:8083/api/ok` (content-service)
- `http://localhost:8084/actuator/health` (notification-service)
- `http://localhost:8085/actuator/health` (search-service)

## API Gateway

Gateway `8080` portundan public auth endpointlerini identity-service'e, protected workspace/content endpointlerini ilgili servislere route eder.

Public endpointler:

- `POST /auth/signup`
- `POST /auth/login`
- `POST /auth/refresh`
- `GET /actuator/health`

Protected endpointlerde `Authorization: Bearer <accessToken>` zorunludur. Gateway tercihen identity-service JWKS endpointini `JWT_JWKS_URI` ile kullanir; yoksa `JWT_PUBLIC_KEY_PATH` veya `JWT_PUBLIC_KEY` fallback'i devam eder. Sadece `token_type=access` tokenlari kabul edilir.

SCIM provisioning (`/scim/v2/**`, ayri bearer token) gateway uzerinden identity-service'e proxylanir; Faz 76 ile grup nesting ve opsiyonel Bulk MVP desteklenir (`docs/scim-provisioning.md`).

Identity-service Faz 18 ile refresh token lifecycle hardening destekler:

- `POST /auth/logout`: authenticated kullanicinin sundugu tek refresh tokeni revoke eder.
- `POST /auth/revoke-all`: authenticated kullanicinin aktif refresh tokenlarini revoke eder.

Bu islemler refresh tokenlari gecersiz kilar; mevcut access tokenlar kisa TTL bitene kadar gecerlidir.

Gateway downstream'e identity headerlarini kendi uretir:

- `X-User-Id`: JWT `sub`
- `X-User-Email`: JWT `email`
- `X-Workspace-Id`: client gonderdiyse UUID validasyonu sonrasi aktarilir

Client'tan gelen `X-User-Id`, `X-User-Email`, `X-User-Roles`, `X-Workspace-Role` headerlari guvenilmez kabul edilir ve temizlenir. Workspace membership/role kontrolu Faz 3'te workspace-service tarafinda yapilacaktir.

Ayrintilar ve curl ornekleri: [`api-gateway/README.md`](api-gateway/README.md)

## Workspace Service

Workspace, notebook, tag ve invitation domainleri `workspace-service` tarafinda yonetilir. Gateway tarafindan uretilen `X-User-Id` header'i servis icinde authorization icin zorunludur; invitation accept akisi `X-User-Email` de ister.

Faz 19 itibariyla workspace-service list endpointleri `PageResponse<T>` envelope doner ve
`page`, `size`, `sort` parametrelerini destekler.

Faz 6 itibariyla content-service icin internal contract endpointleri gercek implementedir:

- `GET /internal/notebooks/{notebookId}/permissions?userId={userId}`
- `GET /internal/workspaces/{workspaceId}/tags/{tagId}/exists?scope=NOTE`

Faz 13 itibariyla internal auth `INTERNAL_AUTH_MODE` ile calisir: `static-token`, `service-jwt` veya `dual`. Production hedefi `service-jwt` modudur; content-service kisa omurlu RS256 service JWT uretir ve workspace-service issuer/audience/scope dogrular. Static token envleri sadece gecis ve rollback icindir. Bu endpointler gateway'e route edilmez.

Ayrintilar ve curl ornekleri: [`workspace-service/README.md`](workspace-service/README.md)

## Content Service

Note current state, immutable note version history, note links, comments, note tags ve basit search `content-service` tarafinda yonetilir. Permission kontrolu workspace-service internal permission contract'i uzerinden yapilir.

Faz 19 itibariyla content-service list/search endpointleri `PageResponse<T>` envelope doner ve
`page`, `size`, `sort` parametrelerini destekler.

Ayrintilar ve curl ornekleri: [`content-service/README.md`](content-service/README.md)

Faz 71 ile content-service, conflict aninda kullanilabilen analyze-only endpointini ekler:
`POST /notes/{noteId}/merge/analyze`. Bu endpoint merge onerisi uretebilir ancak note kaydi yapmaz.
Faz 72 ile `POST /notes/{noteId}/merge/apply` endpointi eklenir; apply yalnizca kullanici aksiyonu
ile calisir ve `expectedRemoteEtag` ile stale merge korumasi yapar.
Faz 73 ile merge analyze/apply akisina privacy-safe observability metricleri, structured loglar ve
Enterprise Console merge status karti eklenir.

## Notification Service

Faz 24 ile `notification-service` invitation ve security email altyapisi icin eklendi. Faz 32
generic HTTP provider, signed provider webhook, bounce/complaint handling ve suppression list
ekler. Faz 33 SPF/DKIM/DMARC readiness, timestamp replay hardening ve internal suppression ops
standardini ekler. Internal send endpointi `X-Service-Authorization` service JWT ile korunur;
webhook route'u user JWT yerine provider signature ile korunur.

Workspace invitation create akisi notification-service'e email request'i gonderir. Notification
request kabul edilmezse invitation transaction rollback olur ve `503 NOTIFICATION_SERVICE_UNAVAILABLE`
doner.

Ayrintilar: [`docs/notification-service.md`](docs/notification-service.md),
[`docs/email-delivery.md`](docs/email-delivery.md),
[`docs/email-provider-integration.md`](docs/email-provider-integration.md),
[`docs/email-webhooks.md`](docs/email-webhooks.md) ve
[`docs/email-suppression.md`](docs/email-suppression.md),
[`docs/email-deliverability.md`](docs/email-deliverability.md),
[`docs/email-dns-records.md`](docs/email-dns-records.md)

## Search Service

Faz 25 ile `search-service` PostgreSQL full-text search tabanli, provider-agnostic search
foundation olarak eklendi. Faz 26 ile content-service note create/update/restore/archive akislari
search indexing outbox'a yazilir; worker retry/backoff ile search-service internal API'sine
gonderir. Search-service hatasi note write transaction'ini bozmaz.

Public search gateway uzerinden `/search/notes` ile calisir. Internal indexing endpointleri
`X-Service-Authorization` service JWT ile korunur.

Ayrintilar: [`docs/search-service.md`](docs/search-service.md),
[`docs/search-indexing.md`](docs/search-indexing.md),
[`docs/search-index-outbox.md`](docs/search-index-outbox.md) ve
[`docs/search-reindexing.md`](docs/search-reindexing.md). Full/workspace/notebook backfill
runbook'u: [`docs/search-reindex-backfill.md`](docs/search-reindex-backfill.md)
Faz 28 ile mark-and-sweep orphan cleanup opt-in olarak eklendi; hard delete yapilmaz. Faz 29 ile
real cleanup oncesi dry-run cleanup ve orphan preview eklendi.
Faz 30 ile notification, content search outbox ve search reindex worker'lari DB lease, worker
instance id, stale recovery ve graceful shutdown davranisi ile multi-pod icin sertlestirildi.

## Observability + Hardening

Faz 5 sonrasi tum servislerde JSON structured logging, `X-Request-Id`, actuator health/metrics/prometheus endpointleri, opsiyonel OTLP HTTP tracing ve standart error response uygulanir.

```bash
export OTEL_ENABLED=false
export OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318/v1/traces
export MANAGEMENT_TRACING_SAMPLING_PROBABILITY=1.0
docker compose up --build
```

Prometheus scrape endpointleri:

- `http://localhost:8080/actuator/prometheus`
- `http://localhost:8081/actuator/prometheus`
- `http://localhost:8082/actuator/prometheus`
- `http://localhost:8083/actuator/prometheus`

Dokumanlar:

- [`docs/observability.md`](docs/observability.md)
- [`docs/observability-alerting.md`](docs/observability-alerting.md)
- [`docs/resilience.md`](docs/resilience.md)
- [`docs/error-codes.md`](docs/error-codes.md)
- [`docs/runtime-security.md`](docs/runtime-security.md)

Faz 12 runtime tenant hardening: workspace-service ve content-service `DB_RUNTIME_*` ile runtime datasource credential, `DB_MIGRATION_*` ile Flyway credential ayrimini destekler. `APP_RLS_ENABLED=true` transaction icinde PostgreSQL `SET LOCAL app.current_workspace_id` uygular; `APP_RLS_STRICT_WORKSPACE_HEADER=true` aggregate-id endpointlerde `X-Workspace-Id` zorunlu hale getirir. FORCE RLS kalici migration degil, `scripts/db/enable-force-rls-*.sql` opt-in scriptidir.

## Docker Compose (full)

Uygulama container’ları da dahil şekilde başlatmak için:

```bash
docker compose up --build -d
```

Opsiyonel observability stack:

```bash
docker compose -f docker-compose.yml -f docker-compose.observability.yml --profile observability up --build
```

Production-like run icin runtime secretleri env ile verin:

```bash
export SPRING_PROFILES_ACTIVE=prod
export JWT_JWKS_URI=http://identity-service:8081/.well-known/jwks.json
export JWT_KEYS_ACTIVE_KID=prod-key-1
export JWT_KEYS_SIGNING_KEYS_0_KID=prod-key-1
export JWT_KEYS_SIGNING_KEYS_0_PRIVATE_KEY_PATH=/run/secrets/jwt_private_key_1
export JWT_KEYS_SIGNING_KEYS_0_PUBLIC_KEY_PATH=/run/secrets/jwt_public_key_1
export DB_RUNTIME_USER=notebook_runtime
export DB_RUNTIME_PASSWORD=<runtime-db-secret>
export DB_MIGRATION_USER=notebook_migrator
export DB_MIGRATION_PASSWORD=<migration-db-secret>
export INTERNAL_AUTH_MODE=service-jwt
export INTERNAL_SERVICE_JWT_PRIVATE_KEY_PATH=/run/secrets/content_service_internal_jwt_private_key
export TRUSTED_SERVICE_CONTENT_SERVICE_PUBLIC_KEY_PATH=/run/secrets/content_service_internal_jwt_public_key
docker compose up --build
```

Smoke test:

```bash
bash scripts/smoke-test.sh
bash scripts/smoke-test-auth-security.sh
```

## Kubernetes / Helm

Faz 14 chart:

- `deploy/helm/notebook-platform`
- `deploy/helm/notebook-platform/values-dev.yaml`
- `deploy/helm/notebook-platform/values-prod.example.yaml`

Render validation:

```bash
bash scripts/helm-template-check.sh
```

Production chart usage assumes external PostgreSQL, external Redis and one of the supported secret
delivery modes: dev-only Helm-managed native Secret, pre-created `existingSecret`, or production
target ExternalSecret. Faz 40 ile frontend de Helm/GitOps modeline dahil edilir; onerilen ingress
modeli ayri hostlar (`app.*` frontend, `api.*` gateway) uzerindendir.

ExternalSecret provider examples are under
`deploy/helm/notebook-platform/examples/external-secrets/`. They contain placeholders only and do
not install a real cloud/Vault integration.

Runtime RLS production rollout is staged in
[`docs/runtime-rls-rollout.md`](docs/runtime-rls-rollout.md). The prod Helm example shows the
steady-state target, but FORCE RLS remains a separate DBA/ops opt-in script and is not applied by
Helm.

Audit events are queryable through service-local internal endpoints:
`GET /internal/audit-events` on identity/workspace/content. These endpoints require service JWT
scope `internal:audit:read` and must not be exposed through the public gateway.

Staging-like Runtime RLS validation:

```bash
./gradlew rlsIntegrationTest
```

This runs Testcontainers-based workspace/content RLS checks with strict workspace headers,
non-owner runtime roles, FORCE RLS enable/disable scripts and cross-tenant negative reads. Deployed
staging evidence can be captured with
[`docs/rls-staging-validation-report-template.md`](docs/rls-staging-validation-report-template.md).

## GitOps Deployment

Faz 20 adds provider-agnostic GitOps preparation:

- `deploy/gitops/environments/dev/values.yaml`
- `deploy/gitops/environments/staging/values.yaml`
- `deploy/gitops/environments/prod/values.yaml`
- `deploy/gitops/argocd/*.yaml`
- [`docs/gitops-deployment.md`](docs/gitops-deployment.md)

Argo CD is the primary documented model; Flux remains an alternative. The manifests are examples
only: they do not install a controller, deploy to a real cluster, push to a real registry or contain
real secrets. Promote immutable image tags through environment values and run post-sync validation
with `scripts/gitops-post-sync-check.sh`.

## Image Signing and Provenance

Faz 21 adds supply-chain hardening preparation:

- Cosign signing/verification scripts
- SLSA-style provenance placeholder script and GitHub Actions attestation hooks
- per-service SPDX SBOM artifact names
- optional Helm image digest rendering
- admission policy examples under `deploy/policies/`

Primary signing strategy is keyless Cosign through GitHub Actions OIDC. Key-based signing remains
documented for enterprise/offline environments. Details:
[`docs/image-signing-provenance.md`](docs/image-signing-provenance.md).

## CI

GitHub Actions quality gate Java 25 ile `./gradlew --no-daemon spotlessCheck`, `./gradlew --no-daemon clean check` ve `./gradlew --no-daemon bootJar` calistirir. Testcontainers integration testleri icin GitHub hosted runner'da Docker kullanilir.

Faz 17 ile CI ayrica local Docker image build, Syft SBOM generation ve Trivy image scan calistirir.
SBOM dosyalari `sbom/*.spdx.json` olarak CI artifact olur; registry push yapilmaz.

Dokumanlar:

- [`docs/security-threat-model.md`](docs/security-threat-model.md)
- [`docs/production-readiness.md`](docs/production-readiness.md)
- [`docs/runtime-security.md`](docs/runtime-security.md)
- [`docs/secret-inventory.md`](docs/secret-inventory.md)
- [`docs/configuration-profiles.md`](docs/configuration-profiles.md)
- [`docs/secret-rotation-runbook.md`](docs/secret-rotation-runbook.md)
- [`docs/external-secrets.md`](docs/external-secrets.md)
- [`docs/internal-service-auth.md`](docs/internal-service-auth.md)
- [`docs/jwt-key-rotation-design.md`](docs/jwt-key-rotation-design.md)
- [`docs/deployment-packaging.md`](docs/deployment-packaging.md)
- [`docs/backup-restore.md`](docs/backup-restore.md)
- [`docs/audit-events.md`](docs/audit-events.md)
- [`docs/audit-query-api.md`](docs/audit-query-api.md)
- [`docs/audit-retention.md`](docs/audit-retention.md)
- [`docs/database-performance.md`](docs/database-performance.md)
- [`docs/database-roles-and-rls.md`](docs/database-roles-and-rls.md)
- [`docs/runtime-rls-rollout.md`](docs/runtime-rls-rollout.md)
- [`docs/kubernetes-deployment.md`](docs/kubernetes-deployment.md)
- [`docs/api-contract-freeze.md`](docs/api-contract-freeze.md)
- [`docs/load-testing.md`](docs/load-testing.md)
- [`docs/supply-chain-security.md`](docs/supply-chain-security.md)
- [`docs/observability-alerting.md`](docs/observability-alerting.md)
- [`docs/service-contract-testing.md`](docs/service-contract-testing.md)
- [`docs/pagination-design.md`](docs/pagination-design.md)
- [`docs/auth-token-revocation.md`](docs/auth-token-revocation.md)
- [`docs/opensearch-provider.md`](docs/opensearch-provider.md)
- [`docs/search-permission-snapshot.md`](docs/search-permission-snapshot.md)
- [`docs/frontend-mvp.md`](docs/frontend-mvp.md)
- [`docs/frontend-e2e.md`](docs/frontend-e2e.md)
- [`docs/frontend-deployment.md`](docs/frontend-deployment.md)
- [`docs/frontend-csp-hardening.md`](docs/frontend-csp-hardening.md)
- [`docs/pwa-offline-read-mode.md`](docs/pwa-offline-read-mode.md)
- [`docs/offline-edit-sync-design.md`](docs/offline-edit-sync-design.md)
- [`docs/offline-data-encryption.md`](docs/offline-data-encryption.md)
- [`docs/enterprise-sso.md`](docs/enterprise-sso.md)
- [`docs/sso-admin-role-mapping.md`](docs/sso-admin-role-mapping.md)
- [`docs/auth-cookie-csrf.md`](docs/auth-cookie-csrf.md)
- [`docs/admin-audit-ui.md`](docs/admin-audit-ui.md)
- [`docs/admin-audit-proxy.md`](docs/admin-audit-proxy.md)
- [`docs/email-provider-integration.md`](docs/email-provider-integration.md)
- [`docs/email-webhooks.md`](docs/email-webhooks.md)
- [`docs/email-suppression.md`](docs/email-suppression.md)
- [`docs/email-deliverability.md`](docs/email-deliverability.md)
- [`docs/email-dns-records.md`](docs/email-dns-records.md)

Frontend note editor currently uses BlockNote with debounced auto-save (Faz 38) while preserving
existing backend `contentBlocks` contract.
Phase 42 adds an operational **Admin / Audit UI** shell; Faz 43 activates secure real-mode access
through gateway admin authorization + server-side audit proxy (`docs/admin-audit-proxy.md`).
Faz 44 adds runtime-configurable CSP hardening and frontend runtime security rollout guidance
(`docs/frontend-csp-hardening.md`).
Faz 45 adds in-app Notification Center UX + user notification APIs on existing
`notification-service` (`docs/notification-center.md`).
Faz 46 adds responsive/mobile shell polish without backend contract changes
(`docs/frontend-responsive.md`).
Faz 47 adds conflict resolution UX for note saves (reload/copy/overwrite) on top of existing
ETag/If-Match contracts, without auto-merge or new backend endpoints.
Faz 48 adds admin audit export foundation (CSV/JSONL), gateway-side redaction and SIEM ingestion
guidance (`docs/audit-export.md`, `docs/audit-siem-integration.md`).
Faz 49 adds notification preferences + channel control (`docs/notification-preferences.md`).
Faz 50 adds MFA/WebAuthn design foundation (`docs/mfa-webauthn-design.md`,
`docs/mfa-rollout-plan.md`).
Faz 51 upgrades MFA to active WebAuthn step-up + recovery flow baseline.
Faz 52 enforces admin MFA on sensitive gateway admin endpoints and strengthens recovery policy.
Faz 53 adds scheduled audit export foundation via ops script/CronJob, archive manifest + checksum,
and WORM/retention runbook preparation (`docs/audit-scheduled-export.md`,
`docs/audit-archive-retention.md`).
Faz 54 adds machine identity auth for scheduled audit export with scoped short-lived JWT policy
(`docs/audit-export-machine-identity.md`).
Faz 55 adds S3-compatible archive upload foundation, object-lock-aware settings, retry policy and
remote verification script (`docs/audit-archive-object-storage.md`).
Faz 56 upgrades Notification Center with SSE-based realtime in-app delivery while preserving polling
fallback (`docs/realtime-notifications-sse.md`).
Faz 57 adds Redis pub/sub fanout for multi-pod SSE notification delivery
(`docs/realtime-notifications-redis-fanout.md`).
Faz 58 adds email digest and quiet-hours delivery scheduling controls
(`docs/notification-digest-quiet-hours.md`).
Faz 59 adds PWA foundation and offline read-only note access with IndexedDB cache
(`docs/pwa-offline-read-mode.md`).
Faz 60 adds Enterprise SSO foundation and admin identity hardening
(`docs/enterprise-sso.md`, `docs/sso-admin-role-mapping.md`).
Faz 61 adds SCIM provisioning and enterprise user lifecycle foundation
(`docs/scim-provisioning.md`, `docs/enterprise-user-lifecycle.md`).
Faz 62 adds streaming SIEM push foundation with identity outbox/worker architecture
(`docs/siem-streaming-push.md`, `docs/siem-event-schema.md`, `docs/siem-provider-generic-http.md`).
Faz 63 adds a read-only **Enterprise Admin Console** (gateway `/admin/enterprise/status`, internal
service JWT aggregation, secret-safe cards + warnings) — see `docs/enterprise-admin-console.md`.
Faz 77 adds **enterprise admin change requests** (validate + `PENDING` records in identity-service;
gateway `/admin/enterprise/change-requests`; no live config mutation). Faz 78 adds **approve/reject**
with four-eyes policy (still no automatic apply) — see `docs/enterprise-admin-write-operations.md`,
`docs/admin-change-request-workflow.md`, and `docs/admin-change-request-approval-workflow.md`.
Faz 79 adds **fine-grained admin RBAC** (`platform_permissions` on JWTs, optional gateway enforce,
SSO/SCIM group mapping) — see `docs/admin-rbac.md` and `docs/admin-permission-matrix.md`.
Faz 86 adds **admin RBAC directory visibility** (read-only UI + internal APIs; source breakdown).
Faz 87 adds **GitOps PR proposals** for approved RBAC grant/revoke change requests into
`deploy/gitops/environments/{env}/admin-rbac-overrides.yaml` (`docs/admin-rbac-gitops-integration.md`).
Faz 88 adds **optional mounted-file runtime ingestion** for that manifest (feature-flagged off by default),
gateway `GET/POST /admin/rbac/overrides/*`, metrics/audit, and read-only UI status — see
`docs/admin-rbac-runtime-overrides.md` (no IdP/SCIM mutation, no direct role API).
Faz 64 adds a **durable DB outbox** for notification SSE fanout with worker retries and dead-letter
semantics while keeping Redis pub/sub as the realtime layer (`docs/notification-durable-fanout.md`).
Faz 81 adds **admin notification delivery analytics** (aggregate-only; `docs/notification-analytics-dashboard.md`).
Faz 82 adds **admin dead-letter list, dry-run, and requeue** for fanout `DEAD` rows with RBAC, MFA on requeue, and idempotency (`docs/notification-dead-letter-requeue.md`).
Faz 83 adds **notification retention planner, optional scheduled worker, and admin purge API/UI** for analytics aggregates, fanout terminal rows, digest/email terminal rows, and dead-letter requeue audit rows — default dry-run / disabled destructive (`docs/notification-retention-worker.md`, `docs/notification-retention-policy.md`). Faz 84 adds **notification legal holds** so destructive retention respects governance (`docs/notification-legal-hold.md`, `docs/retention-governance.md`).
Faz 65 adds **per-workspace notification preference overrides** (channel gates only; digest/quiet
hours stay global) — see `docs/workspace-notification-preferences.md`.
Faz 85 adds **workspace admin managed notification policies** (owner/admin; force channel modes for all members) — see `docs/workspace-notification-policies.md`.
Faz 66 adds **client-side conflict diff** and optional **Apply suggested merge** for BlockNote
content (three-way model; no backend merge endpoint) — see `docs/note-conflict-diff-merge.md`.
Faz 67 adds **offline edit/sync design** and IndexedDB **draft** foundation (flags default off; no
production sync worker) — see `docs/offline-edit-sync-design.md`.
Faz 68 adds **offline edit/sync MVP** behind flags: NotePage offline draft editing, settings draft
list, and manual sync/conflict handling (still no automatic background sync).
Faz 69 adds **offline storage encryption hardening** for drafts/cache with session-bound WebCrypto
key lifecycle and encryption-required guardrails.
Faz 70 adds **offline sync rollout hardening** with rollout modes, stale sync recovery, attempt
limits, and safer conflict/locked draft handling for staged production pilots.
Faz 74 adds **offline foreground background sync design/foundation** with guarded eligibility and
trigger policies (`disabled/prompt/auto_safe`) while keeping production default disabled and manual
sync unchanged.
Faz 75 adds **offline foreground background sync MVP**: app lifecycle triggers, prompt consent flow,
auto-safe sequential batch sync and local summary banners/diagnostics, without service-worker rollout.
