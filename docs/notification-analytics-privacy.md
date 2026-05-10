# Notification analytics — privacy and data minimization (Faz 81)

## Intent

Delivery analytics exist so platform operators can see **health and flow** (created, skipped, queued, sent, failures, fanout backlog) without collecting content or per-user behavior.

## Stored and exposed data

- **Allowed in DB aggregates**: low-cardinality fields such as `notificationType`, `channel`, `severity`, `eventKind`, hourly `bucketStart`, and counts. Skip-related `eventKind` values include `SKIPPED_PREFERENCE`, `SKIPPED_WORKSPACE_PREFERENCE`, and `SKIPPED_WORKSPACE_ADMIN_POLICY` (Faz 85 policy blocks) without storing *why* beyond that coarse category.
- **Not stored**: raw notification body, note title/content, user messages, tokens, email addresses, full metadata blobs, `userId`, `workspaceId` (MVP is global aggregate only).

## Metrics (Micrometer)

Prometheus-style counters may use labels such as `eventKind`, `channel`, `reason` where cardinality is controlled. **Do not** add `userId`, `workspaceId`, or free-text identifiers as labels.

## API responses

The summary JSON is designed for admin dashboards: totals, channel/type breakdowns, and operational snapshots. It must not be extended with user-identifying fields or raw payloads in this phase.

## Access control

- **Human**: `admin:notifications:analytics:read` via gateway.
- **Service**: `internal:admin:notifications:analytics:read` for notification-service internal endpoint.

## Audit

Gateway logs analytics views with time range and bucket parameters only (`admin_notification_analytics_viewed`).

## Retention

Configure `NOTIFICATION_ANALYTICS_RETENTION_DAYS` and apply periodic SQL purge per [`notification-analytics-dashboard.md`](notification-analytics-dashboard.md) until automated purge exists.

## Out of scope (Faz 81)

Per-user read rates, marketing analytics, external BI export, workspace-scoped analytics, and content-level forensics.
