# notebook-platform Helm Chart

Provider-agnostic umbrella chart for the backend services:

- api-gateway
- identity-service
- workspace-service
- content-service

The chart intentionally does not install PostgreSQL, Redis, OTel Collector, Prometheus or a cloud
secret manager. Production deployments should use managed/external dependencies.

## Prerequisites

- Kubernetes 1.28+
- Helm 3
- Built and pushed service images
- External PostgreSQL 16+
- External Redis 7
- A Kubernetes Secret or External Secrets controller that provides runtime secrets

## Render

```bash
helm lint deploy/helm/notebook-platform
helm template notebook-platform deploy/helm/notebook-platform \
  -f deploy/helm/notebook-platform/values-dev.yaml
helm template notebook-platform deploy/helm/notebook-platform \
  -f deploy/helm/notebook-platform/values-prod.example.yaml
helm template notebook-platform deploy/helm/notebook-platform \
  --set secrets.mode=external-secrets \
  --set secrets.externalSecrets.enabled=true \
  --set secrets.externalSecrets.secretStoreRef.name=notebook-platform-secret-store
```

If Helm is not installed, run:

```bash
bash scripts/helm-template-check.sh
```

The script skips validation when Helm is unavailable.

## Install Example

```bash
helm upgrade --install notebook-platform deploy/helm/notebook-platform \
  --namespace notebook-platform \
  --create-namespace \
  -f deploy/helm/notebook-platform/values-prod.yaml
```

Do not use `values-prod.example.yaml` as-is. Copy it to an untracked values file and wire real image
tags, hosts and secret references.

## Secrets

The chart supports three delivery modes:

- `native-kubernetes-secret` with `secrets.create=true`: Helm creates a Secret. Use only for
  local/dev placeholder values.
- `native-kubernetes-secret` with `secrets.existingSecret=<name>`: Helm references a Secret that was
  created outside the chart.
- `external-secrets` with `secrets.externalSecrets.enabled=true`: Helm renders an
  ExternalSecret that syncs from a provider-backed SecretStore. This is the production target.

Production with External Secrets:

```yaml
secrets:
  mode: external-secrets
  externalSecrets:
    enabled: true
    secretStoreRef:
      name: notebook-platform-secret-store
      kind: ClusterSecretStore
    targetSecretName: notebook-platform-secrets
```

Production fallback with a pre-created Kubernetes Secret:

```yaml
secrets:
  mode: native-kubernetes-secret
  create: false
  existingSecret: notebook-platform-secrets
```

The Secret must provide the standardized keys:

- `identity-db-password`
- `workspace-db-runtime-password`
- `workspace-db-migration-password`
- `content-db-runtime-password`
- `content-db-migration-password`
- `redis-password`
- `internal-api-token-primary`
- `internal-api-token-secondary`
- `workspace-internal-api-token-primary`
- `workspace-internal-api-token-secondary`
- `jwt-private-key.pem`
- `jwt-public-key.pem`
- `content-service-jwt-private-key.pem`
- `content-service-jwt-public-key.pem`
- `otel-auth-token` optional

Mounted paths:

- `/etc/notebook/secrets/jwt/private.pem`
- `/etc/notebook/secrets/jwt/public.pem`
- `/etc/notebook/secrets/service-jwt/content-private.pem`
- `/etc/notebook/secrets/service-jwt/content-public.pem`

Provider example `ClusterSecretStore` manifests are under
`examples/external-secrets/`. They are placeholders only; this chart does not install External
Secrets Operator or any cloud integration.

## Network Model

Only api-gateway should be public through Ingress. identity-service, workspace-service and
content-service are ClusterIP only.

Service discovery:

- api-gateway -> `http://<release>-notebook-platform-identity:8081`
- api-gateway -> `http://<release>-notebook-platform-workspace:8082`
- api-gateway -> `http://<release>-notebook-platform-content:8083`
- content-service -> workspace-service
- workspace-service -> identity-service

## Security Context

The chart runs containers as non-root, drops Linux capabilities, disables privilege escalation and
uses a read-only root filesystem. `/tmp` is mounted as `emptyDir` for Java/Spring temporary files.

## Migrations

The chart keeps the current application-startup Flyway model. workspace-service and content-service
receive runtime DB credentials and Flyway migration credentials separately. Production operators may
replace startup Flyway with a dedicated migration Job in a later phase.

## Runtime RLS Rollout

`values-prod.example.yaml` shows the intended steady state:

```yaml
config:
  appRlsEnabled: "true"
  appRlsStrictWorkspaceHeader: "true"
```

Do not treat those values as a one-step production switch. Follow
`docs/runtime-rls-rollout.md`:

1. enable strict header mode while RLS is still disabled
2. enable runtime tenant context
3. switch workspace/content to non-owner runtime DB roles
4. apply FORCE RLS manually with `scripts/db/enable-force-rls-*.sql` after staging validation

Helm does not run FORCE RLS scripts. Use `scripts/rls/*` preflight checks before Stage 3 and Stage 4.

## Observability

- Liveness: `/actuator/health/liveness`
- Readiness: `/actuator/health/readiness`
- Prometheus: `/actuator/prometheus`
- `ServiceMonitor` is optional and disabled by default.
- OTel endpoint is external and configured through values.

Grafana dashboard JSON and Prometheus alert rules live under `observability/`. They are examples for
an external monitoring stack; this chart does not install Grafana, Prometheus or Alertmanager.

## Autoscaling

HPA rendering is disabled by default:

```yaml
autoscaling:
  enabled: false
```

Set `autoscaling.enabled=true` and adjust per-service min/max replicas when deploying to a cluster
with Metrics Server. Keep CPU requests on every service because CPU utilization based HPA depends on
requests.

## Rollback

Use Helm revision rollback:

```bash
helm history notebook-platform -n notebook-platform
helm rollback notebook-platform <revision> -n notebook-platform
```

If service JWT key distribution fails, temporarily set `config.internalAuthMode=dual` or
`static-token` while the trusted key Secret is repaired.

ExternalSecret provider-side updates may not restart pods automatically. Use `kubectl rollout
restart deployment/<service>` or a Secret reloader controller after rotations.

For RLS incidents, first set `config.appRlsStrictWorkspaceHeader=false`, then
`config.appRlsEnabled=false`, then restart workspace/content deployments. If FORCE RLS was applied,
run the corresponding `scripts/db/disable-force-rls-*.sql` script with the migration owner.
