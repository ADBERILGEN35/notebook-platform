# Notification analytics / delivery dashboard (Faz 81)

Privacy-safe, **aggregate-only** operational visibility for notification delivery. This is not product analytics: there is no per-user tracking, no message bodies, and no workspace dimension in the MVP.

## UI

- Route: `/app/admin/notifications/analytics`
- Feature flag (runtime): `FRONTEND_NOTIFICATION_ANALYTICS_ENABLED` → injected as `NOTIFICATION_ANALYTICS_UI_ENABLED` in `window.__NOTEBOOK_CONFIG__` (see `frontend/docker-entrypoint.sh`).
- Admin permission: `admin:notifications:analytics:read` (included for `PLATFORM_OBSERVABILITY_VIEWER` and `PLATFORM_ADMIN` via RBAC).

## APIs

| Layer | Method | Path | Auth |
|-------|--------|------|------|
| api-gateway | GET | `/admin/notifications/analytics/summary?from=&to=&bucket=` | User JWT + enterprise admin enabled + `admin:notifications:analytics:read` |
| notification-service | GET | `/internal/admin/notifications/analytics/summary?from=&to=&bucket=` | Service JWT, scope `internal:admin:notifications:analytics:read` |

Gateway proxies to notification-service using the audit/service JWT signer. Upstream failures map to `502` / `AUDIT_PROXY_REQUEST_FAILED`.

Successful gateway calls log `admin_notification_analytics_viewed` with `from`, `to`, `bucket` (no payload content).

## Configuration (notification-service)

| Env | Purpose |
|-----|---------|
| `NOTIFICATION_ANALYTICS_ENABLED` | Master switch for recording + summary (default `true` in app yaml) |
| `NOTIFICATION_INTERNAL_ADMIN_ANALYTICS_ENABLED` | Exposes internal summary endpoint (default `false`; enable with gateway rollout) |
| `NOTIFICATION_ANALYTICS_RETENTION_DAYS` | Retention cutoff for hourly aggregate rows (optional automatic purge: Faz 83 `docs/notification-retention-worker.md`) |
| `NOTIFICATION_ANALYTICS_MAX_RANGE_DAYS` | Max `from`/`to` span for summary queries |
| `NOTIFICATION_ANALYTICS_BUCKET` | Bucket label in responses (hourly aggregates) |
| `AUDIT_ADMIN_SERVICE_JWT_NOTIFICATION_ALLOWED_SCOPES` | Must include `internal:admin:notifications:analytics:read` for gateway calls |

## Configuration (api-gateway)

| Env | Purpose |
|-----|---------|
| `GATEWAY_ADMIN_ENTERPRISE_ENABLED` | Must be true for analytics admin route |
| `GATEWAY_ADMIN_ENTERPRISE_NOTIFICATION_ANALYTICS_PATH` | Upstream path (default `/internal/admin/notifications/analytics/summary`) |

## Aggregate model

Hourly table: `notification_delivery_analytics_hourly` (Flyway). Dimensions: `notificationType`, `channel`, `severity`, `eventKind`. **No** `userId`, **no** `workspaceId`, **no** raw metadata.

Skip-related event kinds include `SKIPPED_PREFERENCE`, `SKIPPED_WORKSPACE_PREFERENCE`, and `SKIPPED_WORKSPACE_ADMIN_POLICY` (Faz 85: email/in-app skipped because a workspace admin policy force-disabled the channel while the user’s base preference would have allowed delivery).

Live snapshots in the summary response (not hourly rows): fanout outbox counts, digest pending count, SSE connection count (pod-local), worker last-run timestamps.

## Retention / purge (manual)

Automatic purge is optional; operators can delete old buckets after validating backups:

```sql
-- Example: remove aggregates older than NOTIFICATION_ANALYTICS_RETENTION_DAYS (run during maintenance window)
DELETE FROM notification_delivery_analytics_hourly
WHERE bucket_start < NOW() - INTERVAL '90 days';
```

Tune the interval to match `NOTIFICATION_ANALYTICS_RETENTION_DAYS`.

## Runbook

- High **fanout dead** or **email failed/dead** counts: see [`notification-durable-fanout.md`](notification-durable-fanout.md), [`email-delivery.md`](email-delivery.md), [`notification-dead-letter-requeue.md`](notification-dead-letter-requeue.md) (requeue UI when enabled), and [`notification-retention-worker.md`](notification-retention-worker.md) (eligible-row planning/purge).
- **Digest worker disabled**: check `NOTIFICATION_DIGEST_WORKER_ENABLED` / `NOTIFICATION_DIGEST_ENABLED` in notification-service config ([`notification-digest-quiet-hours.md`](notification-digest-quiet-hours.md)).
- **SSE active connections** is per pod; use metrics across replicas for cluster-wide view.

## Related

- [`notification-analytics-privacy.md`](notification-analytics-privacy.md) — data minimization rules
- [`notification-service.md`](notification-service.md) — service overview
- [`notification-dead-letter-requeue.md`](notification-dead-letter-requeue.md) — Faz 82 dead-letter list / requeue (link from UI when enabled)
- [`notification-retention-worker.md`](notification-retention-worker.md) — Faz 83 retention planner / worker / admin purge
