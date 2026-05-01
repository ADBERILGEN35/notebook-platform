# External Secrets

Faz 15 adds a provider-agnostic External Secrets foundation for the Helm chart. The chart can consume
secrets from three delivery modes without changing application containers.

## Delivery Modes

| Mode | Values | Recommended use |
|---|---|---|
| Helm-managed native Secret | `secrets.mode=native-kubernetes-secret`, `secrets.create=true` | Local/dev only |
| Pre-created Kubernetes Secret | `secrets.mode=native-kubernetes-secret`, `secrets.existingSecret=<name>` | Production fallback when External Secrets is unavailable |
| ExternalSecret | `secrets.mode=external-secrets`, `secrets.externalSecrets.enabled=true` | Production target |

Production should prefer External Secrets Operator with a provider-backed `ClusterSecretStore` or
`SecretStore`. The chart does not install the operator or configure a cloud provider.

## Kubernetes Secret Key Contract

Any generated or pre-created Secret must expose these keys:

| Key | Consumer |
|---|---|
| `jwt-private-key.pem` | identity-service signing key mount |
| `jwt-public-key.pem` | identity-service public key and gateway fallback mount |
| `content-service-jwt-private-key.pem` | content-service service JWT signer |
| `content-service-jwt-public-key.pem` | workspace-service trusted service JWT verifier |
| `identity-db-password` | identity-service datasource |
| `workspace-db-runtime-password` | workspace-service runtime datasource |
| `workspace-db-migration-password` | workspace-service Flyway datasource |
| `content-db-runtime-password` | content-service runtime datasource |
| `content-db-migration-password` | content-service Flyway datasource |
| `redis-password` | api-gateway Redis rate limiting |
| `internal-api-token-primary` | workspace-service static fallback |
| `internal-api-token-secondary` | workspace-service static fallback rotation |
| `workspace-internal-api-token-primary` | content-service static fallback |
| `workspace-internal-api-token-secondary` | content-service static fallback rotation |
| `otel-auth-token` | optional OTel exporter auth headers |

The application env var names remain unchanged. Helm maps these Secret keys into the existing env
variables and file paths.

## Mounted Paths

The shared Secret volume is mounted at `/etc/notebook/secrets`:

- `jwt-private-key.pem` -> `/etc/notebook/secrets/jwt/private.pem`
- `jwt-public-key.pem` -> `/etc/notebook/secrets/jwt/public.pem`
- `content-service-jwt-private-key.pem` -> `/etc/notebook/secrets/service-jwt/content-private.pem`
- `content-service-jwt-public-key.pem` -> `/etc/notebook/secrets/service-jwt/content-public.pem`

## ExternalSecret Mapping

`values.yaml` contains the default ExternalSecret mapping list under
`secrets.externalSecrets.data`. Each item maps a Kubernetes Secret key to a provider remote key:

```yaml
secrets:
  mode: external-secrets
  externalSecrets:
    enabled: true
    refreshInterval: 1h
    secretStoreRef:
      name: notebook-platform-secret-store
      kind: ClusterSecretStore
    targetSecretName: notebook-platform-secrets
```

Provider examples live under
`deploy/helm/notebook-platform/examples/external-secrets/` for Vault, AWS Secrets Manager, GCP
Secret Manager and Azure Key Vault. They are placeholders and must be adapted before use.

## Rotation And Rollout

External Secrets Operator refreshes the Kubernetes Secret on its `refreshInterval`. Kubernetes does
not restart pods automatically for every Secret update. The chart adds `checksum/secret` annotations
for Helm-managed values, but ExternalSecret provider-side changes may not change the Helm-rendered
checksum.

Use one of these rollout triggers after secret rotation:

- `kubectl rollout restart deployment/<service> -n <namespace>`
- a reloader controller that watches Secret changes
- a Helm upgrade that changes a rendered value or annotation

JWT and service JWT key rotation still require the documented overlapping-key windows. Database and
Redis password rotation require pod restarts so connection pools reconnect.

## Rollback

- ExternalSecret failure: switch temporarily to a pre-created Secret by setting
  `secrets.mode=native-kubernetes-secret`, `secrets.create=false`, and
  `secrets.existingSecret=<known-good-secret>`.
- Bad key rotation: restore the previous provider value and restart affected deployments.
- Operator outage: existing Kubernetes Secrets remain in-cluster, but new rotations will not sync
  until the operator is healthy.

Never commit real provider credentials, PEM private keys, database passwords, Redis passwords or
tokens to values files.

## GitOps Usage

The GitOps environment values use:

- dev: pre-created Kubernetes Secret placeholder for local or ephemeral clusters.
- staging/prod: `secrets.mode=external-secrets` with environment-specific `ClusterSecretStore`
  placeholder names.

Replace the placeholder SecretStore names before deploying. Do not place provider credentials or
secret values in `deploy/gitops/environments/**/values.yaml`.
