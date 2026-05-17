# Retention datasource Helm example (Faz 110)

Ops template for optional dedicated retention DB credentials. **Default chart values keep all `enabled: false`.**

## Runtime binding (Faz 111)

When `retentionDatasource.<service>.enabled: true` and `CONTENT_RETENTION_DATASOURCE_*` (etc.) env vars
are set, the service creates a dedicated Hikari pool used **only** by retention count repositories.
Startup fails fast if enabled but URL/username/password are incomplete.

- Enable only after DBA role + ExternalSecret exist.
- Use preflight SQL + gateway smoke to validate readiness.
- Normal runtime `DB_*` credentials and JPA paths are unchanged.

## Per-service env names

| Service | Enabled env | URL / user / password env |
|---------|-------------|---------------------------|
| content | `CONTENT_RETENTION_DATASOURCE_ENABLED` | `CONTENT_RETENTION_DATASOURCE_URL`, `_USERNAME`, `_PASSWORD` |
| notification | `NOTIFICATION_RETENTION_DATASOURCE_ENABLED` | `NOTIFICATION_RETENTION_DATASOURCE_*` |
| workspace | `WORKSPACE_RETENTION_DATASOURCE_ENABLED` | `WORKSPACE_RETENTION_DATASOURCE_*` |
| search | `SEARCH_RETENTION_DATASOURCE_ENABLED` | `SEARCH_RETENTION_DATASOURCE_*` |

## Values overlay (no secrets in Git)

```yaml
retentionDatasource:
  content:
    enabled: true
    existingSecret: notebook-platform-secrets
    urlKey: content-retention-datasource-url
    usernameKey: content-retention-datasource-username
    passwordKey: content-retention-datasource-password
  notification:
    enabled: false
  workspace:
    enabled: false
  search:
    enabled: false
```

Populate secret keys via External Secrets (example fragment):

```yaml
# ExternalSecret data entries — remote paths are environment-specific placeholders
- secretKey: content-retention-datasource-url
  remoteRef:
    key: notebook-platform/staging/content-retention-jdbc-url
- secretKey: content-retention-datasource-username
  remoteRef:
    key: notebook-platform/staging/content-retention-jdbc-username
- secretKey: content-retention-datasource-password
  remoteRef:
    key: notebook-platform/staging/content-retention-jdbc-password
```

Use `secrets.existingSecret` at chart level or per-service `retentionDatasource.<service>.existingSecret` for a dedicated Kubernetes Secret.

See [`docs/retention-datasource-ops-handoff.md`](../../../../docs/retention-datasource-ops-handoff.md).
