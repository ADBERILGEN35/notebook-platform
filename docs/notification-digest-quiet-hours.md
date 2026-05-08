# Notification Digest And Quiet Hours (Faz 58)

## Scope

Faz 58 adds user-level delivery scheduling controls for email notifications:

- email digest mode (`DAILY` / `WEEKLY` / `NEVER`)
- quiet hours window with timezone
- security-critical notifications bypass digest and quiet hours

In-app notifications continue realtime delivery through SSE and polling fallback.

Faz 65 **per-workspace notification preferences** do not change digest or quiet hours: those controls remain **global** for the user. Workspace overrides only gate whether a given channel is allowed before digest/quiet-hours rules apply to email.

## APIs

- `GET /notification-delivery-preferences`
- `PATCH /notification-delivery-preferences`

Response/request fields:

- `emailDigestEnabled`
- `emailDigestFrequency`
- `quietHoursEnabled`
- `quietHoursStart`
- `quietHoursEnd`
- `timezone`

## Backend Model

- `user_notification_delivery_preferences`
- `notification_digest_items`

Digest worker runs in notification-service and groups due digest items per user to create
`NOTIFICATION_DIGEST` email queue entries.

## Rules

- `SECURITY_*` notifications are always immediate.
- If digest is enabled for eligible email notifications, items are queued for digest instead of direct
  email queue insertion.
- If quiet hours are active for non-critical immediate emails, send is deferred to next allowed local
  time via `nextAttemptAt`.

## Delivery Semantics

- Best-effort acceleration remains unchanged for in-app SSE.
- DB remains source of truth for preferences, digest items and email queue.
