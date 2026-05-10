# Notification Center (Faz 45 + Faz 56/57 SSE + Faz 64 durable fanout)

## Scope

Faz 45 adds polling-based in-app Notification Center foundation. Faz 56 upgrades it with SSE-based
realtime delivery plus polling fallback. Faz 57 adds Redis fanout for multi-pod SSE propagation.
Faz 64 adds an optional **PostgreSQL fanout outbox** so SSE events are retried after transient Redis /
process failures (`notification-durable-fanout.md`).

- Delivery model: **SSE + polling fallback** (frontend keeps 30s polling for resilience).
- SSE events may be **duplicated** (at-least-once-ish); UI logic must be idempotent.
- Out of scope: WebSocket, external message brokers (Kafka/RabbitMQ), mobile push.

## Backend Model

`notification-service` now includes `UserNotification`:

- Fields: `id`, `recipientUserId`, `workspaceId`, `type`, `title`, `message`, `severity`, `actionUrl`,
  `metadata`, `readAt`, `archivedAt`, `createdAt`, `updatedAt`, `idempotencyKey`.
- Enums:
  - `UserNotificationType`: `WORKSPACE_INVITATION_RECEIVED`, `COMMENT_ADDED`,
    `NOTE_VERSION_RESTORED`, `SECURITY_SESSIONS_REVOKED`, `SYSTEM_NOTICE`
  - `UserNotificationSeverity`: `INFO`, `SUCCESS`, `WARNING`, `CRITICAL`
- Migration: `notification-service/.../V6__create_user_notifications.sql`

## APIs

### Internal Create

`POST /internal/notifications/in-app`

- Auth: `X-Service-Authorization` service JWT
- Scope: `internal:notification:in-app:create`
- Behavior: supports `idempotencyKey`, validates internal `actionUrl` (`/app/*` only), sanitizes metadata.

### Public User APIs (via gateway)

- `GET /notifications`
- `GET /notifications/unread-count`
- `GET /notifications/stream`
- `POST /notifications/{notificationId}/read`
- `POST /notifications/read-all`
- `POST /notifications/{notificationId}/archive`

Rules:

- `recipientUserId` is derived from gateway context header (`X-User-Id`), never from client payload.
- Ownership enforced for read/archive/list.
- Archived notifications are excluded from list/unread.
- SSE stream requires same auth context and returns only current user's events.

## Integration (MVP)

Identity revoke-all now triggers in-app notification request:

- Type: `SECURITY_SESSIONS_REVOKED`
- Severity: `WARNING`
- Action: `/app/settings/security`
- Failure policy: revoke-all flow continues; failures are audited/logged.

Invitation/comment fan-out stays as future work for this phase.

## Frontend UX (Faz 56)

- Topbar bell with unread badge.
- Dropdown: latest 5 notifications, empty state, mark-read/archive, `View all`.
- Page: `/app/notifications` with unread filter, type filter, optional workspace filter, pagination, mark-all-read.
- Feature flag: `NOTIFICATIONS_ENABLED` (`FRONTEND_NOTIFICATIONS_ENABLED` runtime env).
- SSE flag: `NOTIFICATIONS_SSE_ENABLED` (`FRONTEND_NOTIFICATIONS_SSE_ENABLED` runtime env).
- Cookie/dual auth mode uses `EventSource(..., { withCredentials: true })`.
- Bearer mode keeps polling-only behavior.

## Security Notes

- `message` rendered as plain React text.
- `actionUrl` must be internal `/app/*` path.
- Metadata is stored for backend/internal use; UI does not display arbitrary metadata.
- Cookie mode CSRF protections continue for POST read/archive/read-all via gateway.

## Faz 49 Preferences update

- `GET/PATCH /notification-preferences` provides user-level channel controls (`IN_APP`, `EMAIL`).
- Faz 65: `GET/PATCH/POST reset` under `/notification-preferences/workspaces/{workspaceId}` for per-workspace overrides (see `docs/workspace-notification-preferences.md`).
- Security-critical `SECURITY_SESSIONS_REVOKED` is mandatory and cannot be disabled.
- Disabled preference paths may return `SKIPPED` for internal create flows.

## Faz 58 Delivery Schedule

- `GET/PATCH /notification-delivery-preferences` adds email digest + quiet-hours + timezone controls.
- In-app realtime flow stays unchanged.
- Security-critical notifications remain immediate.

## Faz 81 Admin delivery analytics (operational)

Aggregate-only metrics (no per-user or message-body analytics) are recorded in notification-service and exposed via the admin dashboard (`docs/notification-analytics-dashboard.md`). This does not change user-facing Notification Center behavior.
