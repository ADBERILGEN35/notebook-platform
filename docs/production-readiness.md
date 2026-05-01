# Production Readiness Checklist

| Area | Status | Notes | Owner / next step |
|---|---|---|---|
| Secrets | PARTIAL | Provider-agnostic `SecretProvider`, `SecretValue` masking, file/env loading, prod fail-fast checks, ExternalSecret Helm templates and rotation docs exist. | Install/configure a real provider-backed External Secrets setup. |
| JWT key rotation | DONE | identity-service emits `kid`, exposes JWKS, validates refresh tokens by configured key set, and supports user-level refresh token revoke-all. | Add operational alerting for unknown `kid`; evaluate access token blacklist only if short TTL is insufficient. |
| Database migrations | DONE | Flyway is enabled per service with isolated schema history tables. | Add migration rollback/runbook policy. |
| Observability | PARTIAL | JSON logs, request id, metrics, optional OTLP config, starter Grafana dashboards and Prometheus alert rules exist. | Tune thresholds, wire Alertmanager and define retention policy. |
| Security | PARTIAL | Gateway header sanitation, token hashing, secret masking, prod fail-fast checks and internal API token support exist. | Add mTLS/service identity and deployment checks. |
| CI/CD | PARTIAL | CI runs Java 25 `spotlessCheck`, `clean check`, `bootJar`, local image SBOM generation and Trivy image scanning. Faz 20 adds manual draft workflows for image release and GitOps promotion. Faz 21 adds Cosign signing/provenance-ready release steps and SBOM artifact naming. | Add real registry publishing, protected environment approvals and enforced signature/provenance verification. |
| Data backup | PARTIAL | `docs/backup-restore.md` defines pg_dump/restore guidance. | Automate backups and regularly test restore. |
| Rate limiting | DONE | Gateway Redis rate limiting is active and configurable. | Tune production limits after load testing. |
| Actuator exposure | PARTIAL | Health/metrics/prometheus are available; details hidden. | Restrict actuator endpoints by network/auth in production. |
| Internal service authentication | PARTIAL | Static token, service-jwt and dual modes exist; content-service can sign short-lived service JWTs and workspace-service validates issuer/audience/scope. | Roll out service-jwt mode in prod, then add mTLS. |
| RLS runtime enforcement | PARTIAL | workspace/content bind tenant context, support runtime vs migration DB credentials, include non-owner role scripts, preflight SQL, opt-in FORCE RLS scripts, strict workspace header mode, staged rollout docs and Faz 22 staging-like RLS integration tests. | Execute Stage 1-5 in a real staging environment before production. |
| Load testing | PARTIAL | `scripts/smoke-test.sh` covers a happy-path smoke flow. | Add k6 load profiles after stable deployment target exists. |
| Deployment packaging | PARTIAL | Dockerfiles use multi-stage JDK/JRE, non-root runtime and `JAVA_OPTS`; Helm chart renders Deployments, Services, ConfigMap, native Secret/existingSecret/ExternalSecret, Ingress, NetworkPolicy, ServiceMonitor and optional HPA templates. GitOps dev/staging/prod values, Argo CD Application examples and optional image digest rendering exist. | Install a real GitOps controller, configure registry publishing and test rollback against a real cluster. |
| Admission control | PARTIAL | Faz 21 adds Sigstore/Kyverno example policies for signed images, non-latest tags and non-root pods. | Install and enforce a real admission controller in staging before prod. |
| Static formatting | DONE | Spotless + Google Java Format is configured and CI runs `spotlessCheck`. | Keep format gate mandatory in CI. |
| Audit events | PARTIAL | DB audit event tables and service writers exist for critical events. | Add admin query API, retention and alerting policy. |
| API contract freeze | PARTIAL | `docs/api-contract-freeze.md` captures current public/internal contracts. | Add generated OpenAPI contract diff in CI later. |
| Service contract tests | PARTIAL | content-service/workspace-service internal contract tests cover response shape, field presence and consumer path/query compatibility. | Publish shared contract artifacts or add Pact/Spring Cloud Contract later. |
| Pagination | DONE | Workspace/content list endpoints return `PageResponse<T>` with page/size/sort validation and sort allow-lists. | Evaluate cursor pagination for high-growth notes/comments/search after load testing. |
| Refresh token revoke-all | DONE | `POST /auth/logout` and `POST /auth/revoke-all` revoke refresh tokens with audit events and token metadata. | Add future session listing and optional access token introspection/blacklist. |
