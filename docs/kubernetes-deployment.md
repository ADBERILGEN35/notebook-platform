## Faz 34 Network Paths

- Allow egress `search-service -> workspace-service` for internal permission snapshot/membership/check
  calls.
- Allow egress `workspace-service -> search-service` for notebook permission refresh calls.

# Kubernetes Deployment

Faz 14 adds a provider-agnostic Helm chart under
`deploy/helm/notebook-platform`.

## Scope

Included:

- Deployments and ClusterIP Services for api-gateway, identity-service, workspace-service,
  content-service and notification-service.
- ConfigMap for non-secret runtime configuration.
- Native Secret, pre-created Secret and ExternalSecret delivery modes for secret material.
- Optional Ingress that routes only to api-gateway.
- Optional NetworkPolicy.
- Optional Prometheus Operator `ServiceMonitor`.
- Optional HorizontalPodAutoscaler resources, disabled by default.
- Probes, resource requests/limits and non-root security contexts.
- Frontend Deployment/Service/Ingress with runtime config (`FRONTEND_API_BASE_URL`) and `/healthz`.
- Auth transport/CORS cookie settings through Helm values (`AUTH_TOKEN_TRANSPORT`,
  `CORS_ALLOW_CREDENTIALS`, cookie flags).
- Gateway admin audit proxy settings (`GATEWAY_ADMIN_*`, `ADMIN_AUDIT_RATE_LIMIT_*` and
  service JWT path wiring).
- Frontend CSP/runtime security settings through Helm values
  (`FRONTEND_CSP_*` from `frontend.security.csp.*`).
- Enterprise SSO toggles and OIDC provider placeholders (`SSO_*`) with secret-backed client secret.
- SIEM streaming push toggles (`SIEM_*`) with secret-backed auth headers/tokens.

Not included:

- PostgreSQL chart/operator.
- Redis chart/operator.
- Real cloud secret manager integration or External Secrets Operator installation.
- mTLS/service mesh.
- Real GitOps controller installation or cluster deployment. Provider-agnostic Argo CD examples and
  environment values are available under `deploy/gitops`.

## Values

- `values.yaml`: secure defaults and full option surface.
- `values-dev.yaml`: single-replica local cluster profile, no NetworkPolicy or ServiceMonitor.
- `values-prod.example.yaml`: production-oriented example with Ingress and NetworkPolicy enabled.
  It also shows the intended RLS steady-state values, but those must be rolled out through
  [`runtime-rls-rollout.md`](runtime-rls-rollout.md), not enabled blindly in a live environment.
- `deploy/gitops/environments/dev/values.yaml`: dev GitOps overrides.
- `deploy/gitops/environments/staging/values.yaml`: staging GitOps overrides.
- `deploy/gitops/environments/prod/values.yaml`: prod GitOps overrides.

Never place real secrets in values files committed to git.

Secret delivery is controlled by:

- `secrets.mode=native-kubernetes-secret` with `secrets.create=true` for local/dev placeholder
  Secrets.
- `secrets.mode=native-kubernetes-secret` with `secrets.existingSecret=<name>` for pre-created
  Kubernetes Secrets.
- `secrets.mode=external-secrets` with `secrets.externalSecrets.enabled=true` for External Secrets
  Operator.

## External Dependencies

Production should use managed/external PostgreSQL and Redis:

- identity-service uses `externalDatabase.identityUrl`.
- workspace-service uses `externalDatabase.workspaceRuntimeUrl` plus runtime and migration users.
- content-service uses `externalDatabase.contentRuntimeUrl` plus runtime and migration users.
- content-service search indexing outbox worker is controlled by `SEARCH_OUTBOX_*` env values.
  It remains inside content-service; no separate Deployment, Kafka or RabbitMQ is required.
- search-service reindex worker is controlled by `SEARCH_REINDEX_*` env values and calls
  content-service internal source API over ClusterIP.
- content, notification and search pods set `WORKER_INSTANCE_ID` from pod name. Worker-owned
  deployments define `terminationGracePeriodSeconds` so shutdown stops new claims while in-flight
  work finishes.
- `SEARCH_REINDEX_ORPHAN_CLEANUP_ENABLED` defaults to `false`; run dry-run cleanup and inspect
  orphan preview in staging before enabling real cleanup with scoped operator approval.
- notification-service uses `externalDatabase.notificationUrl` and `notification-db-password`.
- api-gateway uses `externalRedis.host`, `externalRedis.port` and `redis-password` from Secret.

The chart keeps startup Flyway for now. A dedicated migration Job is recommended before a strict
production rollout where application pods must not receive migration credentials.

## Secret Mounts

The chart mounts the Secret at `/etc/notebook/secrets`:

- `jwt-private-key.pem` -> `/etc/notebook/secrets/jwt/private.pem`
- `jwt-public-key.pem` -> `/etc/notebook/secrets/jwt/public.pem`
- `content-service-jwt-private-key.pem` -> `/etc/notebook/secrets/service-jwt/content-private.pem`
- `content-service-jwt-public-key.pem` -> `/etc/notebook/secrets/service-jwt/content-public.pem`
- `workspace-service-jwt-private-key.pem` -> `/etc/notebook/secrets/service-jwt/workspace-private.pem`
- `workspace-service-jwt-public-key.pem` -> `/etc/notebook/secrets/service-jwt/workspace-public.pem`

Runtime env points the applications to those paths:

- `JWT_PRIVATE_KEY_PATH`
- `JWT_PUBLIC_KEY_PATH`
- `INTERNAL_SERVICE_JWT_PRIVATE_KEY_PATH`
- `TRUSTED_SERVICE_CONTENT_SERVICE_PUBLIC_KEY_PATH`
- `WORKSPACE_SERVICE_JWT_PRIVATE_KEY_PATH`
- `TRUSTED_SERVICE_WORKSPACE_SERVICE_PUBLIC_KEY_PATH`
- `GATEWAY_ADMIN_AUDIT_SERVICE_JWT_PRIVATE_KEY_PATH`
- `AUDIT_ADMIN_SERVICE_JWT_PUBLIC_KEY_PATH`

notification-service is ClusterIP-only. It is not added to Ingress or api-gateway routes.

search-service is ClusterIP-only and is reachable publicly only through api-gateway `/search/**`.
content-service can reach search-service internal indexing endpoints, and search-service can reach
workspace-service for permission filtering.

`docs/external-secrets.md` defines the full Secret key contract and provider examples.

## Security

The default container security context:

- `runAsNonRoot: true`
- `allowPrivilegeEscalation: false`
- `readOnlyRootFilesystem: true`
- `capabilities.drop: ["ALL"]`

`/tmp` is an `emptyDir` mount because Java and Spring may need temporary filesystem space.

NetworkPolicy is disabled by default for dev. The production example enables it and allows:

- external ingress only to api-gateway
- external ingress to frontend when `frontend.ingress.enabled=true`
- internal traffic among notebook-platform pods
- egress to external DB/Redis/OTel endpoints through configurable CIDR

## Runtime RLS Rollout

`values-prod.example.yaml` sets:

- `config.appRlsStrictWorkspaceHeader: "true"`
- `config.appRlsEnabled: "true"`

Treat those as the production steady-state target. Roll out in stages:

1. Enable strict header mode first.
2. Enable runtime tenant context.
3. Switch to non-owner runtime DB users.
4. Apply FORCE RLS only with opt-in SQL after staging validation.

FORCE RLS is not applied by Helm. Use the DBA-controlled scripts under `scripts/db/` and preflight
checks under `scripts/rls/`.

In GitOps promotion, roll out RLS flags through environment values only after staging validation.
`APP_RLS_STRICT_WORKSPACE_HEADER` and `APP_RLS_ENABLED` are safe application config toggles; FORCE
RLS remains an explicit database operation outside Argo CD/Helm auto-sync.

Faz 22 adds `./gradlew rlsIntegrationTest` and guarded `scripts/rls/run-force-rls-*.sh` wrappers to
validate the staging model before changing cluster values.

## Validation

```bash
bash scripts/helm-template-check.sh
```

When Helm is installed, the script runs:

- `helm lint deploy/helm/notebook-platform`
- `helm template ... -f values-dev.yaml`
- `helm template ... -f values-prod.example.yaml`
- ExternalSecret mode render
- existingSecret mode render

In this development environment Helm may be absent; the script exits successfully with a skip
message so Gradle/Java validation remains unaffected.

## Autoscaling

`autoscaling.enabled=false` by default. When enabled, the chart renders one `autoscaling/v2` HPA per
enabled backend service using the per-service settings under:

- `autoscaling.apiGateway`
- `autoscaling.identity`
- `autoscaling.workspace`
- `autoscaling.content`

CPU utilization based HPA requires valid container CPU requests. The default chart values include
requests; production overrides must keep them or HPA utilization will not work correctly.

## Image Digests

Each service image supports:

- `services.<service>.image.tag`
- `services.<service>.image.digest`

If `digest` is set, Helm renders `repository@sha256:...`. If it is empty, Helm keeps the existing
`repository:tag` behavior. GitOps should keep immutable tags everywhere and prefer digest pinning in
prod once registry digest capture and Cosign verification are stable.

## GitOps

Faz 20 adds provider-agnostic GitOps examples:

- `deploy/gitops/argocd/app-dev.yaml`
- `deploy/gitops/argocd/app-staging.yaml`
- `deploy/gitops/argocd/app-prod.yaml`
- `docs/gitops-deployment.md`

The examples use Argo CD as the primary controller model and keep Flux as a documented alternative.
They do not install Argo CD, deploy to a real cluster or include real registry/secret credentials.

Frontend ingress recommendation:

- separate hosts (`app.*` frontend, `api.*` gateway) as default
- same-host path-based routing remains optional future model

## OpenSearch Provider

The chart does not install OpenSearch and does not declare an OpenSearch chart dependency.
Configure `search.provider`, `search.dualWriteEnabled`, `search.fallbackToPostgres` and
`search.opensearch.*` for an external or managed endpoint. Credentials are read from Secret keys
`opensearch-username` and `opensearch-password` by default. If egress is restricted, add an
environment-specific NetworkPolicy allow rule for search-service to the external OpenSearch
endpoint.

## Email Provider And Webhooks

The chart keeps notification-service as an internal ClusterIP service. api-gateway may route
`/webhooks/email/**` to notification-service so external providers can deliver signed events without
user JWT. Keep `EMAIL_WEBHOOKS_ENABLED=false` until an ExternalSecret-backed `email-webhook-secret`
is present, `EMAIL_WEBHOOK_REQUIRE_TIMESTAMP=true` is set and ingress/network allow rules are
reviewed.

Provider API keys are Secret values (`email-provider-api-key`). Webhook secrets are Secret values
(`email-webhook-secret`). Do not put provider keys or webhook secrets into ConfigMaps or GitOps
plain values.

Run `scripts/email/provider-readiness-check.sh` with the environment values before enabling a real
provider.

## Faz 45 additions

- Notification-service in-app API toggle: `NOTIFICATIONS_IN_APP_ENABLED=true`.
- Frontend runtime toggle via ConfigMap: `FRONTEND_NOTIFICATIONS_ENABLED=true`.
- Gateway route forwards `/notifications/**` to `notification-service` (protected user route).

## Faz 56 additions

- Notification-service SSE toggles:
  - `NOTIFICATIONS_SSE_ENABLED`
  - `NOTIFICATIONS_SSE_HEARTBEAT_SECONDS`
  - `NOTIFICATIONS_SSE_TIMEOUT_SECONDS`
  - `NOTIFICATIONS_SSE_MAX_CONNECTIONS_PER_USER`
- Frontend runtime toggle: `FRONTEND_NOTIFICATIONS_SSE_ENABLED`.
- Gateway route includes dedicated `GET /notifications/stream` mapping with long response timeout for
  SSE.
- Multi-pod note: current SSE emitter registry is in-memory per pod; keep polling fallback enabled.

## Faz 57 additions

- Redis pub/sub fanout toggle for notification-service:
  - `NOTIFICATIONS_SSE_DISTRIBUTED_ENABLED`
  - `NOTIFICATIONS_SSE_DISTRIBUTED_PROVIDER`
  - `NOTIFICATIONS_SSE_REDIS_CHANNEL`
  - `NOTIFICATIONS_SSE_DISTRIBUTED_PUBLISH_LOCAL_FIRST`
- `NOTIFICATION_INSTANCE_ID` is sourced from pod name (Downward API) for self-echo skip.
- Notification deployment now consumes `REDIS_PASSWORD` and `REDIS_SSL_ENABLED` for secure Redis
  connectivity.

## Faz 58 additions

- Notification delivery schedule toggles:
  - `NOTIFICATION_DIGEST_ENABLED`
  - `NOTIFICATION_DIGEST_WORKER_ENABLED`
  - `NOTIFICATION_DIGEST_POLL_INTERVAL_SECONDS`
  - `NOTIFICATION_DIGEST_BATCH_SIZE`
  - `NOTIFICATION_DIGEST_MAX_ITEMS_PER_EMAIL`
  - `NOTIFICATION_DIGEST_DAILY_SEND_TIME`
  - `NOTIFICATION_DIGEST_WEEKLY_DAY`
  - `NOTIFICATION_DIGEST_WEEKLY_SEND_TIME`
- Gateway route forwards `/notification-delivery-preferences/**` to notification-service.

## Faz 64 additions

- Durable SSE fanout outbox (`NOTIFICATION_FANOUT_*` envs); see `docs/notification-durable-fanout.md`.
- Helm values: `notificationFanoutOutboxEnabled`, `notificationFanoutWorkerEnabled`,
  `notificationFanoutImmediateLocalDelivery`, batch/backoff/lock retention keys (see chart `values.yaml`).

## Faz 65 additions

- `WORKSPACE_NOTIFICATION_PREFERENCES_ENABLED`, `NOTIFICATION_WORKSPACE_CLIENT_*` JWT settings for
  notification-service → workspace-service.
- Workspace-service: `TRUSTED_SERVICE_NOTIFICATION_SERVICE_*` for validating those internal calls.
- Frontend: `FRONTEND_WORKSPACE_NOTIFICATION_PREFERENCES_ENABLED` (Settings → workspace notification section).

## Faz 48 additions

- Gateway audit export toggle: `GATEWAY_ADMIN_AUDIT_EXPORT_ENABLED`.
- Gateway audit export limits:
  - `GATEWAY_ADMIN_AUDIT_EXPORT_MAX_RANGE_DAYS`
  - `GATEWAY_ADMIN_AUDIT_EXPORT_MAX_RECORDS`
  - `GATEWAY_ADMIN_AUDIT_EXPORT_PAGE_SIZE`
- Dedicated export rate limit:
  - `ADMIN_AUDIT_EXPORT_RATE_LIMIT_REPLENISH_RATE`
  - `ADMIN_AUDIT_EXPORT_RATE_LIMIT_BURST_CAPACITY`
  - `ADMIN_AUDIT_EXPORT_RATE_LIMIT_REQUESTED_TOKENS`

## Faz 49 additions

- Notification preference feature flag: `NOTIFICATION_PREFERENCES_ENABLED`.
- Frontend runtime toggle: `FRONTEND_NOTIFICATION_PREFERENCES_ENABLED`.

## Faz 50 additions

- Identity MFA flags:
  - `MFA_ENABLED`
  - `MFA_WEBAUTHN_ENABLED`
  - `MFA_CHALLENGE_TTL_SECONDS`
  - `MFA_REQUIRED_FOR_PLATFORM_ADMIN`
- Frontend runtime toggle: `FRONTEND_MFA_UI_ENABLED`.

## Faz 53 additions

- Optional CronJob template: `templates/cronjob-audit-export.yaml` (default disabled).
- Values path: `auditExport.scheduled.*`.
- CronJob requires an ops image that contains `scripts/audit/scheduled-export-audit-events.sh`.
- Secret placeholders:
  - `audit-export-bearer-token`
  - `audit-export-admin-cookie`
  (do not commit real credentials).

## Faz 54 additions

- Scheduled export supports machine JWT mode (`AUTH_MODE=machine`).
- CronJob can mount machine private key PEM for short-lived JWT signing.
- Gateway machine auth flags (`GATEWAY_AUDIT_EXPORT_MACHINE_AUTH_*`) gate non-human export access.

## Faz 55 additions

- Scheduled export CronJob supports S3-compatible upload env contract (`AUDIT_ARCHIVE_*`, `AWS_*`).
- Upload credentials are Secret-backed (`access-key-id`, `secret-access-key`) and optional when upload disabled.
- Object lock headers are configurable for governance/compliance retention.
- Cron image should include both `curl` and `aws` CLI capabilities (default chart value uses `amazon/aws-cli`).
