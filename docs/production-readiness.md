## Faz 53 readiness checks

- Admin MFA rollout values explicitly set per environment (`off/warn/observe` progression).
- Scheduled export remains disabled by default until machine identity auth is approved.
- Archive package verification (`manifest + sha256`) is part of compliance runbook.
- Legal-hold and retention procedures are documented in `docs/audit-archive-retention.md`.

## Faz 54 readiness checks

- Machine identity config (`GATEWAY_AUDIT_EXPORT_MACHINE_AUTH_*`) is validated in staging.
- Scheduled export script can generate short-lived JWT without leaking token in logs.
- Non-export endpoints reject machine token usage.

## Faz 55 readiness checks

- S3-compatible upload dry-run succeeds in staging bucket/prefix.
- Upload retry and fail-if-exists controls are validated.
- Object lock values are tested in non-production bucket before compliance mode enablement.

## Faz 56 readiness checks

- `NOTIFICATIONS_SSE_ENABLED` and `FRONTEND_NOTIFICATIONS_SSE_ENABLED` are pinned per environment.
- Cookie/dual auth environments confirm SSE connect success; bearer-only environments use polling
  fallback.
- Gateway `GET /notifications/stream` route and timeout behavior are validated in staging.
- SSE connection rejection and send-failure metrics are observed under load.

## Faz 57 readiness checks

- Redis fanout is validated with at least two notification-service replicas in staging.
- `notifications_sse_distributed_publish_failures_total` and subscriber error metrics remain stable.
- Cross-pod delivery smoke is executed before production enablement.
- Production rollout starts with `NOTIFICATIONS_SSE_DISTRIBUTED_ENABLED=false`, then canary enablement.

## Faz 58 readiness checks

- Digest worker and schedule values are explicitly pinned per environment.
- Staging validates digest grouping and quiet-hours delay behavior.
- Security-critical notifications are tested to ensure immediate delivery bypass.

## Faz 62 readiness checks

- `SIEM_PUSH_ENABLED` productionda kontrollu rollout ile acilmali.
- `SIEM_PROVIDER=generic-http` ise endpoint/auth config startup validationdan gecmeli.
- Outbox `PENDING/DEAD` count metricleri ve worker health gozlenmeli.
- Rollback icin `SIEM_PUSH_ENABLED=false` ile worker/push hizla devre disi birakilabilir.

## Faz 71 readiness checks

- `NOTE_MERGE_ANALYSIS_ENABLED` should start `false` in production; enable first in dev/staging.
- `NOTE_MERGE_SUPPORTED_VERSIONS` must be pinned (`1`) across frontend/backend for rollout consistency.
- `FRONTEND_BACKEND_MERGE_ANALYSIS_ENABLED` can be enabled gradually while keeping client fallback.
- Validate merge-analysis logs/metrics do not include raw note content.

## Faz 72 readiness checks

- Keep `NOTE_MERGE_APPLY_ENABLED=false` in production until dev/staging conflict scenarios pass.
- Roll out `FRONTEND_BACKEND_MERGE_APPLY_ENABLED` after backend apply endpoint is validated.
- Verify `NOTE_MERGE_REMOTE_CHANGED` and `NOTE_MERGE_CONFLICTS` paths in staging UX.
- Confirm idempotency key table retention/cleanup policy (`NOTE_MERGE_IDEMPOTENCY_TTL_HOURS`).

## Faz 73 readiness checks

- Confirm merge metrics are scraped (`note_merge_*`) and labels remain low-cardinality.
- Validate enterprise status aggregation includes content merge slice and partial-status warnings.
- Keep `NOTE_MERGE_AUDIT_FAILURES_ENABLED=false` in prod until failure-event noise is assessed.

## Faz 74 readiness checks

- Keep `FRONTEND_OFFLINE_BACKGROUND_SYNC_ENABLED=false` in production by default.
- Validate `prompt` mode first in dev/staging before any `auto_safe` pilot.
- Ensure session/encryption guardrails block runs when auth/key state is unavailable.
- Confirm diagnostics contain counters only (no raw note content, titles, ids).

## Faz 75 readiness checks

- Validate lifecycle triggers (`online`, `focus`, boot, settings/manual) do not create retry storms.
- Confirm prompt mode requires explicit consent before batch sync.
- Confirm auto-safe mode skips active-note/conflict/failed/locked drafts.
- Keep production defaults: `OFFLINE_BACKGROUND_SYNC_ENABLED=false`, mode `disabled`.

## Faz 96 readiness checks

- Keep `FRONTEND_SW_BACKGROUND_SYNC_ENABLED=false` and `FRONTEND_SW_BACKGROUND_SYNC_REGISTER_ENABLED=false`
  in production.
- Treat Service Worker Background Sync as opportunistic because Safari/iOS and Firefox do not support it.
- Keep `FRONTEND_SW_BACKGROUND_SYNC_DRY_RUN_ONLY=true` until auth, CSRF, encryption key and lock/lease
  designs are explicitly approved.
- Confirm SW diagnostics contain aggregate counters only and no raw note content.
## Faz 34 Readiness Notes

- Configure `SEARCH_PERMISSION_SNAPSHOT_ENABLED`, `SEARCH_PERMISSION_RUNTIME_CHECK_ENABLED` and
  `SEARCH_PERMISSION_REFRESH_ENABLED`.
- Monitor snapshot fetch/runtime check/refresh metrics for spikes before promotion.
- Keep fallback and fail-closed behavior enabled; do not disable runtime checks for restricted docs.

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
| Audit events | PARTIAL | DB audit event tables, service writers, internal service-JWT query endpoints, retention docs and manual purge examples exist. | Add SIEM/export, immutable archive and Admin UI later. |
| Notifications/email | PARTIAL | notification-service queues provider-agnostic email requests, supports log/noop/smtp/generic-http providers, retries with idempotency, sends workspace invitation emails through service JWT, records provider lifecycle events, handles delivered/bounce/complaint webhooks, maintains suppression state, exposes internal suppression ops and documents SPF/DKIM/DMARC readiness. | Validate a real provider, DNS records and provider-specific webhook verification in staging. |
| Search | PARTIAL | search-service stores PostgreSQL FTS search documents, content-service indexes note lifecycle changes through a retryable DB outbox, search-service can run pull-based reindex jobs with opt-in mark-and-sweep orphan archive cleanup plus dry-run orphan preview, and Faz 31 adds OpenSearch provider projection with PostgreSQL fallback. Faz 30 adds DB lease based worker coordination for email, search outbox and reindex workers. | Validate OpenSearch against a real staging cluster, then add production cleanup rollout and ranking/highlighting later. |
| API contract freeze | PARTIAL | `docs/api-contract-freeze.md` captures current public/internal contracts. | Add generated OpenAPI contract diff in CI later. |
| Service contract tests | PARTIAL | content-service/workspace-service internal contract tests cover response shape, field presence and consumer path/query compatibility. | Publish shared contract artifacts or add Pact/Spring Cloud Contract later. |
| Note optimistic concurrency | PARTIAL | content-service emits note `ETag`, supports `If-Match` on update/restore with `400/412/428` contracts, audit events and metrics (`note_conflict_detected_total`, `note_update_without_if_match_total`, `note_precondition_required_total`). | Roll out frontend If-Match first, then enable `CONTENT_REQUIRE_IF_MATCH_FOR_NOTE_UPDATE=true` in production. |
| Frontend deployment runtime model | PARTIAL | Frontend has Docker image, nginx hardening headers, runtime API base URL injection (`FRONTEND_API_BASE_URL`), Helm frontend deployment/service/ingress and GitOps environment values; admin/audit runtime flags ship in Faz 42 (default off in prod). | Validate in real cluster with TLS, ingress and smoke/e2e gates before production switch. |
| Frontend CSP hardening | PARTIAL | Faz 44 adds runtime-configurable CSP (`disabled/report-only/enforce`), additional browser isolation headers and rollout-ready Helm/GitOps values. | Observe report-only violations in staging, then enforce in production with rollback toggles. |
| Frontend PWA/offline read mode | PARTIAL | Faz 59 adds manifest + service worker foundation, IndexedDB note cache and offline read-only UX. Faz 67 adds offline **draft** schema + policy utilities (flags default off; no production sync worker). | Validate device policy/privacy guidance, tune cache/draft caps per environment; keep `FRONTEND_OFFLINE_EDIT_ENABLED` false in prod until manual sync UX is validated. |
| Break-glass token revocation | PARTIAL | Faz 93 adds break-glass denylist + active token revoke foundations (`jti` based). | Keep `BREAK_GLASS_REVOCATION_ENABLED=false` and `GATEWAY_BREAK_GLASS_DENYLIST_CHECK_ENABLED=false` until staging validation completes; then enable together. |
| Break-glass token rotation governance | PARTIAL | Faz 94 adds rotation-event state machine (REQUIRED → ACKNOWLEDGED → VERIFIED → CLOSED), short hash-fingerprint verification, admin UI, and audit/metrics. No token or raw hash is ever shown. | Keep `BREAK_GLASS_STATIC_TOKEN_ROTATION_TRACKING_ENABLED=false` and `BREAK_GLASS_ROTATION_API_ENABLED=false` until External Secret/runbook flow is rehearsed in staging; then enable tracking before API. |
| Offline edit/sync MVP | PARTIAL | Faz 68 adds flag-gated offline edit for cached notes, draft autosave, settings draft list, and manual sync/conflict handling. | Keep both offline edit/sync flags disabled in prod by default; run staged validation for conflict/permission/error paths before wider rollout. |
| Offline data encryption hardening | PARTIAL | Faz 69 adds optional WebCrypto encryption for drafts and cache, memory-only key lifecycle, and encryption-required guardrail for offline edit. | Validate browser support policy, monitor UX fallback behavior, and enable in sensitive enterprise environments before enabling offline edit. |
| Offline sync rollout hardening | PARTIAL | Faz 70 adds rollout modes, stale-sync recovery, max attempts, conflict retry guardrails, and diagnostics counters. | Keep prod defaults conservative (`sync disabled`), run guarded pilot first, and monitor failure/conflict ratios before expanding cohort. |
| Offline foreground background sync foundation | PARTIAL | Faz 74 adds app-level foreground background sync policy/service foundation with `disabled/prompt/auto_safe`, guarded eligibility and local diagnostics; no production auto-sync rollout by default. | Keep prod disabled, validate prompt/auto-safe in staging, then decide MVP rollout scope in next phase. |
| Offline foreground background sync MVP | PARTIAL | Faz 75 wires app lifecycle triggers, prompt consent flow, auto-safe sequential batch runs, summary/prompt banners and expanded local diagnostics while preserving manual sync path. | Keep prod disabled, run staged dev/staging pilot with prompt first, then auto-safe canary. |
| Enterprise SSO / admin identity hardening | PARTIAL | Faz 60 adds OIDC SSO foundation, external identity mapping and platform_roles claim mapping with allowlist coexistence. | Validate real IdP integration in staging, keep SSO disabled in prod until secrets + claims policy are approved. |
| In-app notification center | PARTIAL | Faz 45 adds polling-based in-app notifications. Faz 49 adds user channel preferences. Faz 56 adds SSE realtime updates (`/notifications/stream`) with polling fallback. Faz 57 adds Redis pub/sub fanout. Faz 58 adds digest/quiet-hours delivery scheduling for non-critical emails. Faz 64 adds optional PostgreSQL durable fanout outbox + worker retries (`docs/notification-durable-fanout.md`). Faz 81 adds **admin-only** aggregate delivery analytics (no message bodies / per-user analytics; `docs/notification-analytics-dashboard.md`). Faz 82 adds **admin dead-letter** list/dry-run/requeue for fanout `DEAD` rows with RBAC, MFA on requeue, idempotency (`docs/notification-dead-letter-requeue.md`). Faz 83 adds **retention planner/worker** for bounded purge of aggregates and terminal notification rows (`docs/notification-retention-worker.md`). Faz 84 adds **notification legal holds** so retention purge respects governance (`docs/notification-legal-hold.md`, `docs/retention-governance.md`). Faz 85 adds **workspace admin notification policies** (owner/admin; `docs/workspace-notification-policies.md`) on top of per-user workspace preferences. | Platform-wide hold, per-tenant retention, automated audit purge, external BI. |
| Cookie auth + CSRF | PARTIAL | Auth transport supports `bearer|cookie|dual`, gateway cookie token extraction and CSRF double-submit checks are implemented, frontend cookie mode supports `credentials: include` and CSRF header injection. | Run staged rollout (`dual` -> frontend cookie mode -> prod cookie-only) and monitor 401/403 spikes. |
| Admin / audit UI/export | PARTIAL | Faz 43 adds gateway admin audit proxy (`/admin/audit-events`) with admin allowlist/role checks and server-side service JWT fan-out. Faz 48 adds bounded export endpoint (`/admin/audit-events/export`) with CSV/JSONL, redaction and separate rate limit bucket. Faz 63 adds read-only enterprise admin console status (`/admin/enterprise/status`, `docs/enterprise-admin-console.md`). Faz 77 adds audited **change requests** (`/admin/enterprise/change-requests`, `docs/enterprise-admin-write-operations.md`) without live config apply. Faz 78 adds **four-eyes approve/reject** (`docs/admin-change-request-approval-workflow.md`) still without runtime apply. **Faz 79** adds fine-grained admin RBAC (`docs/admin-rbac.md`, `platform_permissions` in JWT, optional `GATEWAY_ADMIN_RBAC_ENFORCE`). **Faz 87** appends approved RBAC rows to GitOps `admin-rbac-overrides.yaml`. **Faz 88** adds optional **mounted-file** ingestion + visibility (`docs/admin-rbac-runtime-overrides.md`), status/validate admin routes, and UI card — **defaults off** in prod; no IdP/SCIM mutation. | Enable RBAC in staging with real IdP/SCIM groups; then scheduled exports, direct SIEM streaming; GitOps PR automation after approval. Pilot override ingestion in staging with ConfigMap mount + monitoring before prod. |
| MFA / WebAuthn | PARTIAL | Faz 51 adds active WebAuthn/recovery endpoints, MFA step-up session flow and token issuance after MFA verification. | Harden full cryptographic verification coverage, enforce admin policy by default and complete recovery/support UX. |
| Pagination | DONE | Workspace/content list endpoints return `PageResponse<T>` with page/size/sort validation and sort allow-lists. | Evaluate cursor pagination for high-growth notes/comments/search after load testing. |
| Refresh token revoke-all | DONE | `POST /auth/logout` and `POST /auth/revoke-all` revoke refresh tokens with audit events and token metadata. | Add future session listing and optional access token introspection/blacklist. |

## Faz 76 readiness checks

- SCIM group nesting flags (`SCIM_GROUP_NESTING_*`) and bulk flags (`SCIM_BULK_*`) pinned per environment; bulk remains **off** in prod by default.
- Staging validates nested admin group → `PLATFORM_ADMIN` claim and cycle/depth rejection paths before enabling bulk.
- Operators acknowledge **non-transactional** bulk semantics (`docs/scim-bulk-operations.md`).

## Faz 97 readiness checks

- `SCIM_DELTA_SYNC_ENABLED=false` and `SCIM_DELTA_SYNC_MODE=disabled` remain the production defaults.
- Provider capability flags are diagnostics metadata only; they do not start scheduled sync or provider-specific API calls.
- `FRONTEND_SCIM_COMPATIBILITY_DIAGNOSTICS_ENABLED=false` by default; enable only for read-only admin diagnostics validation.
- Validate provider behavior against `docs/scim-provider-compatibility.md` before any future provider-specific delta POC.
