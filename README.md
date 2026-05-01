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

## API Gateway

Gateway `8080` portundan public auth endpointlerini identity-service'e, protected workspace/content endpointlerini ilgili servislere route eder.

Public endpointler:

- `POST /auth/signup`
- `POST /auth/login`
- `POST /auth/refresh`
- `GET /actuator/health`

Protected endpointlerde `Authorization: Bearer <accessToken>` zorunludur. Gateway tercihen identity-service JWKS endpointini `JWT_JWKS_URI` ile kullanir; yoksa `JWT_PUBLIC_KEY_PATH` veya `JWT_PUBLIC_KEY` fallback'i devam eder. Sadece `token_type=access` tokenlari kabul edilir.

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
target ExternalSecret. Only api-gateway should be exposed through Ingress.

ExternalSecret provider examples are under
`deploy/helm/notebook-platform/examples/external-secrets/`. They contain placeholders only and do
not install a real cloud/Vault integration.

Runtime RLS production rollout is staged in
[`docs/runtime-rls-rollout.md`](docs/runtime-rls-rollout.md). The prod Helm example shows the
steady-state target, but FORCE RLS remains a separate DBA/ops opt-in script and is not applied by
Helm.

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

