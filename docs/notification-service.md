# Notification Service

Faz 24 introduces `notification-service` as a provider-agnostic backend service for operational
email delivery. It is internal-only and is not routed by api-gateway.

## Internal API

`POST /internal/notifications/email`

Headers:

- `X-Service-Authorization: Bearer <service-jwt>`

Required service JWT:

- `aud=notification-service`
- `token_type=service`
- `scope` contains `internal:notification:email:send`

Request:

```json
{
  "type": "WORKSPACE_INVITATION",
  "recipientEmail": "user@example.com",
  "subject": "Workspace invitation",
  "templateKey": "workspace-invitation",
  "templateVariables": {
    "workspaceName": "workspace",
    "inviterEmail": "owner@example.com",
    "role": "ADMIN",
    "acceptUrl": "https://app.example.com/invitations/accept?token=..."
  },
  "idempotencyKey": "workspace-invitation:<invitationId>"
}
```

Response:

```json
{
  "notificationId": "11111111-1111-1111-1111-111111111111",
  "status": "PENDING"
}
```

Normal user access tokens are not accepted. The endpoint is for internal service callers only.

Suppression operations are also internal-only:

```http
GET /internal/email/suppressions
POST /internal/email/suppressions
POST /internal/email/suppressions/{id}/release
```

Required scopes are `internal:notification:suppression:read` for listing and
`internal:notification:suppression:manage` for create/release.

## Domain Model

`email_notifications` stores DB-backed email queue state:

- `PENDING`: queued or retryable
- `SENDING`: worker has claimed the row
- `SENT`: provider accepted the message
- `FAILED`: max attempts exhausted
- `CANCELLED`: reserved for operator workflows

Supported types:

- `WORKSPACE_INVITATION`
- `SECURITY_REFRESH_TOKENS_REVOKED`
- `SECURITY_LOGIN_NEW_DEVICE`
- `SECURITY_PASSWORD_CHANGED`
- `GENERIC_SECURITY_NOTICE`

## Retry And Idempotency

The service uses a database-backed queue. No RabbitMQ or Kafka dependency is introduced in this
phase.

- duplicate `idempotencyKey` returns the existing notification
- worker sends due `PENDING` notifications
- due rows are claimed with `FOR UPDATE SKIP LOCKED` to avoid duplicate sends across concurrent
  workers
- claimed rows store `lockedBy` and `lockExpiresAt`
- expired `SENDING` leases are recovered to `PENDING`
- provider failure increments `attemptCount`
- retry delay uses exponential backoff capped by `EMAIL_RETRY_MAX_DELAY_SECONDS`
- max attempts moves the notification to `FAILED`

For workspace invitations, workspace-service uses
`workspace-invitation:<invitationId>` as the idempotency key.

## Templates

Templates are resource based:

- `templates/email/workspace-invitation.html`
- `templates/email/workspace-invitation.txt`
- `templates/email/security-refresh-tokens-revoked.html`
- `templates/email/security-refresh-tokens-revoked.txt`

Template variables are rendered with a small whitelist-based renderer. HTML templates escape
variables before insertion. Do not add invitation tokens, SMTP credentials, service JWTs or private
keys to audit metadata or logs.

## Audit Events

notification-service writes internal audit events:

- `EMAIL_NOTIFICATION_QUEUED`
- `EMAIL_NOTIFICATION_SENT`
- `EMAIL_NOTIFICATION_FAILED`
- `EMAIL_NOTIFICATION_STALE_RECOVERED`
- `EMAIL_NOTIFICATION_CANCELLED` reserved for future operator actions
- `EMAIL_PROVIDER_WEBHOOK_RECEIVED`
- `EMAIL_DELIVERED`
- `EMAIL_BOUNCED`
- `EMAIL_COMPLAINED`
- `EMAIL_SUPPRESSED`
- `EMAIL_SUPPRESSION_CREATED`
- `EMAIL_SUPPRESSION_MANUALLY_CREATED`
- `EMAIL_SUPPRESSION_RELEASED`
- `EMAIL_WEBHOOK_SIGNATURE_REJECTED`

Email addresses are masked in notification audit metadata. Accept URLs are not written to audit
metadata because invitation URLs contain plaintext tokens.

## Provider Lifecycle

Faz 32 adds `generic-http` / `sendgrid` provider mode, provider delivery status fields and
webhook-driven delivered/bounce/complaint handling. PostgreSQL stores provider events and
suppression state. See:

- [`email-provider-integration.md`](email-provider-integration.md)
- [`email-webhooks.md`](email-webhooks.md)
- [`email-suppression.md`](email-suppression.md)
- [`email-deliverability.md`](email-deliverability.md)
- [`email-dns-records.md`](email-dns-records.md)

`SENT` still means provider accepted the message. Delivery lifecycle is tracked separately through
`deliveryStatus`.

## Limitations

- No unsubscribe center/marketing preference model.
- No central audit service.
- No provider-specific SDK.
- No real DNS/SPF/DKIM/DMARC setup.
- No event-driven workspace outbox; if notification-service rejects an invitation email request,
  workspace-service rolls the invitation transaction back.

## Faz 45/56 In-App Notifications

Notification-service now also exposes in-app user notifications with SSE realtime support:

- Internal create: `POST /internal/notifications/in-app` with scope
  `internal:notification:in-app:create`.
- Public user API: `/notifications`, `/notifications/unread-count`, `/notifications/{id}/read`,
  `/notifications/read-all`, `/notifications/{id}/archive`.
- Realtime stream API: `GET /notifications/stream` (`text/event-stream`, gateway user context required).
- Faz 57 adds Redis pub/sub fanout for multi-pod SSE propagation.
- Data model: `user_notifications` table (`V6__create_user_notifications.sql`) with ownership,
  read/archive lifecycle, and idempotent internal creation.

See [`notification-center.md`](notification-center.md) and
[`realtime-notifications-sse.md`](realtime-notifications-sse.md) and
[`realtime-notifications-redis-fanout.md`](realtime-notifications-redis-fanout.md) for flow details.

## Faz 49 Notification Preferences

- New table: `user_notification_preferences` (`V7__create_user_notification_preferences.sql`).
- New public API: `GET/PATCH /notification-preferences`.
- Preference validation now uses explicit errors:
  - `NOTIFICATION_PREFERENCE_ACCESS_DENIED`
  - `INVALID_NOTIFICATION_PREFERENCE_REQUEST`
  - `MANDATORY_NOTIFICATION_PREFERENCE`
  - `NOTIFICATION_PREFERENCE_NOT_FOUND`

## Faz 58 Digest / Quiet Hours

- New user delivery preference API:
  - `GET /notification-delivery-preferences`
  - `PATCH /notification-delivery-preferences`
- New tables:
  - `user_notification_delivery_preferences`
  - `notification_digest_items`
- Digest worker groups due items and creates `NOTIFICATION_DIGEST` email queue entries.
- Security-critical email notifications bypass digest/quiet-hours and stay immediate.

## Faz 65 Per-workspace notification preferences

- Table: `user_workspace_notification_preferences` (see `V11__user_workspace_notification_preferences.sql`).
- Public API: `GET/PATCH /notification-preferences/workspaces/{workspaceId}` and `POST .../reset`.
- Resolution via `NotificationPreferenceResolver`; workspace-service membership check for API calls.
- Digest/quiet hours remain global; see [`workspace-notification-preferences.md`](workspace-notification-preferences.md).

## Admin status (Faz 63)

`GET /internal/admin/status/notification` (service JWT, opt-in) returns SSE/digest/email **configuration class**
flags only — see `docs/enterprise-admin-console.md`.

## Durable SSE fanout outbox (Faz 64)

- Table: `notification_fanout_outbox` (`V10__notification_fanout_outbox.sql`).
- In-app mutations enqueue secret-safe SSE payloads; optional immediate publish + worker retries.
- See [`notification-durable-fanout.md`](notification-durable-fanout.md).

## Delivery analytics aggregates (Faz 81)

- Table: `notification_delivery_analytics_hourly` — hourly counts by `eventKind` / channel / type / severity (no user/workspace dimensions in MVP).
- Internal summary: `GET /internal/admin/notifications/analytics/summary` when `NOTIFICATION_INTERNAL_ADMIN_ANALYTICS_ENABLED=true` (service JWT scope `internal:admin:notifications:analytics:read`).
- Recording is best-effort: failures are logged/metered and must not fail user-visible notification delivery.
- See [`notification-analytics-dashboard.md`](notification-analytics-dashboard.md) and [`notification-analytics-privacy.md`](notification-analytics-privacy.md).
