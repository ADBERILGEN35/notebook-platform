# Production Readiness Checklist

| Area | Status | Notes | Owner / next step |
|---|---|---|---|
| Secrets | PARTIAL | Provider-agnostic `SecretProvider`, `SecretValue` masking, file/env loading, prod fail-fast checks and rotation docs exist. | Add real Vault/AWS/GCP/Azure adapter and deployment injection. |
| JWT key rotation | DONE | identity-service emits `kid`, exposes JWKS and validates refresh tokens by configured key set; gateway can validate through JWKS URI. | Add operational alerting for unknown `kid` and session revoke-all for emergency rotations. |
| Database migrations | DONE | Flyway is enabled per service with isolated schema history tables. | Add migration rollback/runbook policy. |
| Observability | PARTIAL | JSON logs, request id, metrics, optional OTLP config exist. | Add dashboards, alerts, retention policy. |
| Security | PARTIAL | Gateway header sanitation, token hashing, secret masking, prod fail-fast checks and internal API token support exist. | Add mTLS/service identity and deployment checks. |
| CI/CD | DONE | CI runs Java 25 `spotlessCheck`, `clean check` and `bootJar`. | Add image build/sign/publish in deployment phase. |
| Data backup | PARTIAL | `docs/backup-restore.md` defines pg_dump/restore guidance. | Automate backups and regularly test restore. |
| Rate limiting | DONE | Gateway Redis rate limiting is active and configurable. | Tune production limits after load testing. |
| Actuator exposure | PARTIAL | Health/metrics/prometheus are available; details hidden. | Restrict actuator endpoints by network/auth in production. |
| Internal service authentication | PARTIAL | Static internal token supports primary/secondary rotation; legacy token envs are rejected in prod. | Replace or supplement with mTLS/short-lived service credentials. |
| RLS runtime enforcement | PARTIAL | workspace/content bind tenant context, support runtime vs migration DB credentials, include non-owner role scripts, opt-in FORCE RLS scripts and strict workspace header mode. | Roll out non-owner runtime users, then enable `APP_RLS_ENABLED`/strict header and FORCE RLS per tested table group. |
| Load testing | PARTIAL | `scripts/smoke-test.sh` covers a happy-path smoke flow. | Add k6 load profiles after stable deployment target exists. |
| Deployment packaging | PARTIAL | Dockerfiles use multi-stage JDK/JRE, non-root runtime and `JAVA_OPTS`. | Add image scanning, SBOM, immutable tags. |
| Static formatting | DONE | Spotless + Google Java Format is configured and CI runs `spotlessCheck`. | Keep format gate mandatory in CI. |
| Audit events | PARTIAL | DB audit event tables and service writers exist for critical events. | Add admin query API, retention and alerting policy. |
| API contract freeze | PARTIAL | `docs/api-contract-freeze.md` captures current public/internal contracts. | Add generated OpenAPI contract diff in CI later. |
