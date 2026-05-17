# Retention Datasource Ops Handoff (Faz 110)

Cross-workspace aggregate retention dry-run counts require a **dedicated database role and connection** that is separate from the normal tenant-scoped runtime datasource. This document is the operations handoff for DBAs and platform engineers. It does **not** apply SQL via Flyway and does **not** enable retention automatically in production.

## Helm / GitOps (Faz 110 template + Faz 111 runtime binding)

Chart values: `deploy/helm/notebook-platform/values.yaml` → `retentionDatasource.*`

| Servis | Helm block | Pod env (when `enabled: true`) | Spring property prefix |
|--------|------------|------------------------------|----------------------|
| content-service | `retentionDatasource.content` | `CONTENT_RETENTION_DATASOURCE_*` | `content.retention.datasource` |
| notification-service | `retentionDatasource.notification` | `NOTIFICATION_RETENTION_DATASOURCE_*` | `notification.platform-retention.datasource` |
| workspace-service | `retentionDatasource.workspace` | `WORKSPACE_RETENTION_DATASOURCE_*` | `workspace.retention.datasource` |
| search-service | `retentionDatasource.search` | `SEARCH_RETENTION_DATASOURCE_*` | `search.retention.datasource` |

**Faz 111 behavior:** When `*_RETENTION_DATASOURCE_ENABLED=true` and URL/username/password are set, retention count repositories use a dedicated Hikari pool. Incomplete config → startup `IllegalStateException` (no password in message). When `enabled=false` (default), repositories use the primary runtime `DataSource` as before. Normal JPA/user-facing paths are unchanged.

Dry-run and gateway integration remain controlled by existing flags (separate from datasource):

- `CONTENT_RETENTION_DRY_RUN_ENABLED`, `CONTENT_RETENTION_INTEGRATION_ENABLED`
- `NOTIFICATION_RETENTION_DRY_RUN_COUNTS_ENABLED`, `NOTIFICATION_RETENTION_INTEGRATION_ENABLED`
- `WORKSPACE_RETENTION_DRY_RUN_COUNTS_ENABLED`, `WORKSPACE_RETENTION_INTEGRATION_ENABLED`
- `SEARCH_RETENTION_DRY_RUN_COUNTS_ENABLED`, `SEARCH_RETENTION_INTEGRATION_ENABLED`

Example: [`deploy/helm/notebook-platform/examples/retention-datasource/README.md`](../deploy/helm/notebook-platform/examples/retention-datasource/README.md)

## Secret strategy (no values in Git)

1. DBA creates roles and passwords in the secret manager (not in Git).
2. Platform team maps keys into Kubernetes via **External Secrets** or `secrets.existingSecret`.
3. Helm references keys only:

```yaml
retentionDatasource:
  content:
    enabled: true
    existingSecret: ""  # empty = chart secretName
    urlKey: content-retention-datasource-url
    usernameKey: content-retention-datasource-username
    passwordKey: content-retention-datasource-password
```

Per-service dedicated secret (recommended blast-radius):

```yaml
retentionDatasource:
  content:
    enabled: true
    existingSecret: notebook-platform-content-retention
    urlKey: jdbc-url
    usernameKey: username
    passwordKey: password
```

Shared secret with prefixed keys (acceptable for non-prod only): one `existingSecret`, distinct `urlKey` / `usernameKey` / `passwordKey` per service block.

## DBA role examples (documentation only — not migrations)

Run manually as migration owner / superuser. Replace passwords from secret manager.

### Content — `notebook_content_retention`

```sql
CREATE ROLE notebook_content_retention LOGIN PASSWORD '<from-secret-manager>' BYPASSRLS;
GRANT CONNECT ON DATABASE notebook_platform TO notebook_content_retention;
GRANT USAGE ON SCHEMA public TO notebook_content_retention;
GRANT SELECT ON note_versions, comments, search_index_outbox TO notebook_content_retention;
```

### Notification — `notebook_notification_retention`

```sql
CREATE ROLE notebook_notification_retention LOGIN PASSWORD '<from-secret-manager>' BYPASSRLS;
GRANT CONNECT ON DATABASE notebook_platform TO notebook_notification_retention;
GRANT USAGE ON SCHEMA public TO notebook_notification_retention;
GRANT SELECT ON notification_delivery_analytics_hourly,
  notification_fanout_outbox, notification_dead_letter_requeue_requests,
  notification_digest_items, email_notifications TO notebook_notification_retention;
```

### Workspace — `notebook_workspace_retention`

```sql
CREATE ROLE notebook_workspace_retention LOGIN PASSWORD '<from-secret-manager>' BYPASSRLS;
GRANT CONNECT ON DATABASE notebook_platform TO notebook_workspace_retention;
GRANT USAGE ON SCHEMA public TO notebook_workspace_retention;
GRANT SELECT ON invitations, workspace_audit_events TO notebook_workspace_retention;
```

### Search — `notebook_search_retention`

```sql
CREATE ROLE notebook_search_retention LOGIN PASSWORD '<from-secret-manager>' BYPASSRLS;
GRANT CONNECT ON DATABASE notebook_platform TO notebook_search_retention;
GRANT USAGE ON SCHEMA public TO notebook_search_retention;
GRANT SELECT ON search_documents, search_reindex_jobs TO notebook_search_retention;
```

## Minimum privileges

| Capability | Required | Must NOT grant |
|------------|----------|----------------|
| `CONNECT` on service DB | Yes | — |
| `USAGE` on schema | Yes | — |
| `SELECT` on retention tables | Yes | `INSERT`, `UPDATE`, `DELETE`, `TRUNCATE` |
| `BYPASSRLS` or connection `row_security=off` | One of (see matrix) | `SUPERUSER`, `CREATEROLE`, `CREATEDB` |

## BYPASSRLS vs `row_security=off` decision matrix

| Approach | When to choose | Risk |
|----------|----------------|------|
| Role `BYPASSRLS` | Single retention role per service; DBA prefers explicit role flag | Role must never be reused by runtime app pool |
| JDBC `options=-c row_security=off` | Minimize role capabilities; BYPASSRLS not allowed by policy | Misconfigured pool falls back to RLS → silent zero counts |

Both are **retention-connection only**. Never share with `*_runtime` roles.

## Preflight SQL order (read-only)

1. Content — `scripts/retention/check-retention-rls-readiness.sql`
2. Notification — `scripts/retention/check-notification-retention-rls-readiness.sql`
3. Workspace — `scripts/retention/check-workspace-retention-rls-readiness.sql`
4. Search — `scripts/retention/check-search-retention-rls-readiness.sql`

## Smoke order (gateway)

With `EXPECT_*_RETENTION_READY=true` after dry-run/integration flags enabled:

1. `content-retention-dry-run-smoke.sh`
2. `notification-retention-dry-run-smoke.sh`
3. `workspace-retention-dry-run-smoke.sh`
4. `search-retention-dry-run-smoke.sh`

Or orchestrator: `scripts/retention/run-retention-staging-smoke.sh` (CI / staging).

## Rollback

1. `retentionDatasource.<service>.enabled: false` (GitOps / Helm).
2. `*_RETENTION_INTEGRATION_ENABLED: false` (gateway merge off).
3. `*_RETENTION_DRY_RUN_*_ENABLED: false` (service counts off).
4. Revoke or rotate retention role password if compromise suspected (DBA).

No destructive purge is part of this handoff.

## Faz 112 — Staging dedicated datasource rollout (E2E)

**Applied in Git:** staging `values.yaml` keeps `retentionDatasource.*.enabled: false` (conservative default). **Enable overlay is prepared but not active** until secret manager keys exist.

| Artifact | Purpose |
|----------|---------|
| [`deploy/gitops/environments/staging/values.yaml`](../deploy/gitops/environments/staging/values.yaml) | Active staging — datasource **disabled** |
| [`retention-datasource-enable.overlay.example.yaml`](../deploy/gitops/environments/staging/retention-datasource-enable.overlay.example.yaml) | Optional merge — `enabled: true` + `existingSecret` key refs only |
| [`externalsecret-retention-keys.example.yaml`](../deploy/gitops/environments/staging/externalsecret-retention-keys.example.yaml) | ExternalSecret `secretKey` / `remoteRef` naming guide |
| [`scripts/retention/staging-dedicated-retention-e2e-checklist.md`](../scripts/retention/staging-dedicated-retention-e2e-checklist.md) | Ordered steps: Helm → preflight → pod env → smoke → artifact → metrics → rollback |

**CI evidence (Faz 109):** workflow `Retention Readiness`, job `retention-staging-smoke`, artifact `retention-staging-smoke-evidence`, GitHub Step Summary domain table.

**Production:** prod GitOps unchanged — `retentionDatasource` remains disabled.

## Faz 113 — Safe actuator diagnostics (no secrets)

Each of the four services exposes a **Spring Boot Actuator health component** (not a new admin REST API). Use after deploy to confirm datasource state without reading pod env values in tickets.

| Servis | Actuator component id | Typical check |
|--------|----------------------|---------------|
| content-service | `contentRetentionDataSourceHealth` | `GET /actuator/health` (authorized) |
| notification-service | `notificationRetentionDataSourceHealth` | same |
| workspace-service | `workspaceRetentionDataSourceHealth` | same |
| search-service | `searchRetentionDataSourceHealth` | same |

**Safe detail keys only:** `retentionDatasourceEnabled`, `configComplete`, `usingDedicatedDatasource`, `fallbackToPrimary`, `poolConfigured`, `lastCheckStatus`, `warningCodes`.

**Never present in health JSON:** JDBC URL, username, password, host, database name, raw SQL exception text.

| `lastCheckStatus` | Meaning | Component health (typical) |
|-------------------|---------|----------------------------|
| `DISABLED` | `enabled=false` (default) — counts use primary pool | UP |
| `NOT_CONFIGURED` | `enabled=true` but incomplete config | UP (startup should fail-fast before traffic) |
| `FALLBACK_PRIMARY` | Config complete but no dedicated bean | UP |
| `UP` | Dedicated pool + `Connection.isValid(2)` OK | UP |
| `DOWN` | Dedicated pool present, connectivity failed | DOWN (`RETENTION_DATASOURCE_CONNECTION_FAILED`) |

`management.endpoint.health.show-details` remains **`never`** in service `application.yml`; fields are still safe if a future env enables details for authenticated ops only.

Gateway platform retention plan (`serviceSummaries`) was **not** extended in Faz 113.

## Faz 114 — Staging E2E evidence runbook closure

When staging dedicated datasource is enabled, collect **sanitized evidence** for the change request (no JDBC, tokens, raw SQL, or API bodies).

| Artifact | Path |
|----------|------|
| E2E procedure + green gates | [`staging-dedicated-retention-e2e-checklist.md`](../scripts/retention/staging-dedicated-retention-e2e-checklist.md) |
| CR checklist (fill-in) | [`staging-retention-change-request-evidence-checklist.md`](../scripts/retention/staging-retention-change-request-evidence-checklist.md) |
| Evidence formats (actuator, smoke, metrics, preflight, rollback) | [`retention-staging-e2e-evidence-formats.md`](../scripts/retention/retention-staging-e2e-evidence-formats.md) |
| Blank template generator | `bash scripts/retention/generate-retention-e2e-evidence-template.sh` |

**CI smoke bundle:** workflow `Retention Readiness` → artifact `retention-staging-smoke-evidence` (`retention-staging-smoke-results.json`, `retention-staging-smoke-summary.md`) + GitHub Step Summary.

**Staging green (summary):** preflight all pass; four actuator `UP` with dedicated pool; four smoke domains `passed` (exit 0); artifact validate OK; metrics reviewed; rollback drill documented.

## Related runbooks

- [`retention-rls-production-runbook.md`](retention-rls-production-runbook.md)
- [`notification-retention-rls-production-runbook.md`](notification-retention-rls-production-runbook.md)
- [`workspace-retention-rls-production-runbook.md`](workspace-retention-rls-production-runbook.md)
- [`search-retention-rls-production-runbook.md`](search-retention-rls-production-runbook.md)
- [`platform-retention-governance.md`](platform-retention-governance.md)
