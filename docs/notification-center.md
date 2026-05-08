# Notification Center (Faz 45 MVP)

## Scope

Faz 45 adds an in-app Notification Center foundation without realtime delivery.

- Delivery model: **polling** (frontend refreshes unread count every 30s, list on dropdown open/page actions).
- Out of scope: WebSocket/SSE, Kafka/RabbitMQ, preferences center, mention fan-out, mobile polish.

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
- `POST /notifications/{notificationId}/read`
- `POST /notifications/read-all`
- `POST /notifications/{notificationId}/archive`

Rules:

- `recipientUserId` is derived from gateway context header (`X-User-Id`), never from client payload.
- Ownership enforced for read/archive/list.
- Archived notifications are excluded from list/unread.

## Integration (MVP)

Identity revoke-all now triggers in-app notification request:

- Type: `SECURITY_SESSIONS_REVOKED`
- Severity: `WARNING`
- Action: `/app/settings/security`
- Failure policy: revoke-all flow continues; failures are audited/logged.

Invitation/comment fan-out stays as future work for this phase.

## Frontend UX

- Topbar bell with unread badge.
- Dropdown: latest 5 notifications, empty state, mark-read/archive, `View all`.
- Page: `/app/notifications` with unread filter, type filter, optional workspace filter, pagination, mark-all-read.
- Feature flag: `NOTIFICATIONS_ENABLED` (`FRONTEND_NOTIFICATIONS_ENABLED` runtime env).

## Security Notes

- `message` rendered as plain React text.
- `actionUrl` must be internal `/app/*` path.
- Metadata is stored for backend/internal use; UI does not display arbitrary metadata.
- Cookie mode CSRF protections continue for POST read/archive/read-all via gateway.
