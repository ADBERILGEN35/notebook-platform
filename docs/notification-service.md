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

## Faz 45 In-App Notifications

Notification-service now also exposes in-app user notifications (polling-first MVP):

- Internal create: `POST /internal/notifications/in-app` with scope
  `internal:notification:in-app:create`.
- Public user API: `/notifications`, `/notifications/unread-count`, `/notifications/{id}/read`,
  `/notifications/read-all`, `/notifications/{id}/archive`.
- Data model: `user_notifications` table (`V6__create_user_notifications.sql`) with ownership,
  read/archive lifecycle, and idempotent internal creation.

See [`notification-center.md`](notification-center.md) for UI/API flow details.

## Faz 49 Notification Preferences

- New table: `user_notification_preferences` (`V7__create_user_notification_preferences.sql`).
- New public API: `GET/PATCH /notification-preferences`.
- Preference validation now uses explicit errors:
  - `NOTIFICATION_PREFERENCE_ACCESS_DENIED`
  - `INVALID_NOTIFICATION_PREFERENCE_REQUEST`
  - `MANDATORY_NOTIFICATION_PREFERENCE`
  - `NOTIFICATION_PREFERENCE_NOT_FOUND`
