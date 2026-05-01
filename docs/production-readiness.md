# Production Readiness Checklist

| Area | Status | Notes | Owner / next step |
|---|---|---|---|
| Secrets | PARTIAL | Provider-agnostic `SecretProvider`, `SecretValue` masking, file/env loading, prod fail-fast checks, ExternalSecret Helm templates and rotation docs exist. | Install/configure a real provider-backed External Secrets setup. |
| JWT key rotation | DONE | identity-service emits `kid`, exposes JWKS, validates refresh tokens by configured key set, and supports user-level refresh token revoke-all. | Add operational alerting for unknown `kid`; evaluate access token blacklist only if short TTL is insufficient. |
| Database migrations | DONE | Flyway is enabled per service with isolated schema history tables. | Add migration rollback/runbook policy. |
| Observability | PARTIAL | JSON logs, request id, metrics, optional OTLP config, starter Grafana dashboards and Prometheus alert rules exist. | Tune thresholds, wire Alertmanager and define retention policy. |
| Security | PARTIAL | Gateway header sanitation, token hashing, secret masking, prod fail-fast checks and internal API token support exist. | Add mTLS/service identity and deployment checks. |
| CI/CD | PARTIAL | CI runs Java 25 `spotlessCheck`, `clean check`, `bootJar`, local image SBOM generation and Trivy image scanning. | Add image signing, provenance and publish/deploy in deployment phase. |
| Data backup | PARTIAL | `docs/backup-restore.md` defines pg_dump/restore guidance. | Automate backups and regularly test restore. |
| Rate limiting | DONE | Gateway Redis rate limiting is active and configurable. | Tune production limits after load testing. |
| Actuator exposure | PARTIAL | Health/metrics/prometheus are available; details hidden. | Restrict actuator endpoints by network/auth in production. |
| Internal service authentication | PARTIAL | Static token, service-jwt and dual modes exist; content-service can sign short-lived service JWTs and workspace-service validates issuer/audience/scope. | Roll out service-jwt mode in prod, then add mTLS. |
| RLS runtime enforcement | PARTIAL | workspace/content bind tenant context, support runtime vs migration DB credentials, include non-owner role scripts, preflight SQL, opt-in FORCE RLS scripts, strict workspace header mode and staged rollout docs. | Execute Stage 1-5 in staging before production. |
| Load testing | PARTIAL | `scripts/smoke-test.sh` covers a happy-path smoke flow. | Add k6 load profiles after stable deployment target exists. |
| Deployment packaging | PARTIAL | Dockerfiles use multi-stage JDK/JRE, non-root runtime and `JAVA_OPTS`; Helm chart renders Deployments, Services, ConfigMap, native Secret/existingSecret/ExternalSecret, Ingress, NetworkPolicy, ServiceMonitor and optional HPA templates. | Add immutable tags and a real deploy pipeline. |
| Static formatting | DONE | Spotless + Google Java Format is configured and CI runs `spotlessCheck`. | Keep format gate mandatory in CI. |
| Audit events | PARTIAL | DB audit event tables and service writers exist for critical events. | Add admin query API, retention and alerting policy. |
| API contract freeze | PARTIAL | `docs/api-contract-freeze.md` captures current public/internal contracts. | Add generated OpenAPI contract diff in CI later. |
| Service contract tests | PARTIAL | content-service/workspace-service internal contract tests cover response shape, field presence and consumer path/query compatibility. | Publish shared contract artifacts or add Pact/Spring Cloud Contract later. |
| Pagination | DONE | Workspace/content list endpoints return `PageResponse<T>` with page/size/sort validation and sort allow-lists. | Evaluate cursor pagination for high-growth notes/comments/search after load testing. |
| Refresh token revoke-all | DONE | `POST /auth/logout` and `POST /auth/revoke-all` revoke refresh tokens with audit events and token metadata. | Add future session listing and optional access token introspection/blacklist. |
