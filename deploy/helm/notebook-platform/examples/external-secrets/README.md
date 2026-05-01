# External Secrets Provider Examples

These manifests are provider-agnostic starting points for the External Secrets Operator.
They contain placeholders only and must be adapted before use in any environment.

The Helm chart expects the generated Kubernetes Secret to contain the standardized keys
listed in `docs/external-secrets.md`, for example `jwt-private-key.pem`,
`identity-db-password`, and `content-service-jwt-private-key.pem`.
