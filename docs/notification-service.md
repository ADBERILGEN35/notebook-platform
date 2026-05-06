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
- `EMAIL_NOTIFICATION_CANCELLED` reserved for future operator actions

Email addresses are masked in notification audit metadata. Accept URLs are not written to audit
metadata because invitation URLs contain plaintext tokens.

## Limitations

- No bounce or complaint webhook handling.
- No provider-specific SDK.
- No user notification preference or unsubscribe model.
- No central audit service.
- No event-driven workspace outbox; if notification-service rejects an invitation email request,
  workspace-service rolls the invitation transaction back and returns `503`.
