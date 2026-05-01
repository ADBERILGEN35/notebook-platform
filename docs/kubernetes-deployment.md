# Kubernetes Deployment

Faz 14 adds a provider-agnostic Helm chart under
`deploy/helm/notebook-platform`.

## Scope

Included:

- Deployments and ClusterIP Services for api-gateway, identity-service, workspace-service and
  content-service.
- ConfigMap for non-secret runtime configuration.
- Native Secret, pre-created Secret and ExternalSecret delivery modes for secret material.
- Optional Ingress that routes only to api-gateway.
- Optional NetworkPolicy.
- Optional Prometheus Operator `ServiceMonitor`.
- Optional HorizontalPodAutoscaler resources, disabled by default.
- Probes, resource requests/limits and non-root security contexts.

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
- api-gateway uses `externalRedis.host`, `externalRedis.port` and `redis-password` from Secret.

The chart keeps startup Flyway for now. A dedicated migration Job is recommended before a strict
production rollout where application pods must not receive migration credentials.

## Secret Mounts

The chart mounts the Secret at `/etc/notebook/secrets`:

- `jwt-private-key.pem` -> `/etc/notebook/secrets/jwt/private.pem`
- `jwt-public-key.pem` -> `/etc/notebook/secrets/jwt/public.pem`
- `content-service-jwt-private-key.pem` -> `/etc/notebook/secrets/service-jwt/content-private.pem`
- `content-service-jwt-public-key.pem` -> `/etc/notebook/secrets/service-jwt/content-public.pem`

Runtime env points the applications to those paths:

- `JWT_PRIVATE_KEY_PATH`
- `JWT_PUBLIC_KEY_PATH`
- `INTERNAL_SERVICE_JWT_PRIVATE_KEY_PATH`
- `TRUSTED_SERVICE_CONTENT_SERVICE_PUBLIC_KEY_PATH`

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
