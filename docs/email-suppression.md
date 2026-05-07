# Email Suppression

Recipient suppression prevents repeated sends to bounced, complained or manually suppressed addresses.

## Domain

`email_suppressions` stores:

- normalized email
- reason: `BOUNCE`, `COMPLAINT`, `MANUAL`, `UNSUBSCRIBE`
- provider and provider event id
- source
- created and optional expiry timestamps
- optional release timestamp

The table enforces uniqueness on `lower(email)`. Active behavior is determined by `released_at is null` and `expires_at is null or expires_at > now`.

## Behavior

Before enqueue, notification-service checks the active suppression list. If the recipient is suppressed, the request returns:

```text
409 EMAIL_RECIPIENT_SUPPRESSED
```

The provider is not called.

Webhook behavior:

- `BOUNCE` marks the notification as bounced and creates a `BOUNCE` suppression.
- `COMPLAINT` marks the notification as complained and creates a `COMPLAINT` suppression.
- `DELIVERED` updates delivery status only.

Workspace invitation behavior:

- workspace-service maps notification-service `409` to `409 NOTIFICATION_RECIPIENT_SUPPRESSED`.
- invitation creation fails instead of creating an invitation with no deliverable email.

Marketing unsubscribe/preferences are not part of Faz 32.

## Internal Ops API

Suppression management is internal-only and requires `X-Service-Authorization` service JWT.

```http
GET /internal/email/suppressions?email=user@example.com&reason=MANUAL&activeOnly=true&page=0&size=50
POST /internal/email/suppressions
POST /internal/email/suppressions/{id}/release
```

Scopes:

- Read: `internal:notification:suppression:read`
- Create/release: `internal:notification:suppression:manage`

Manual create request:

```json
{
  "email": "user@example.com",
  "reason": "MANUAL",
  "expiresAt": null
}
```

Active suppressions block notification enqueue and worker sends. Expired or released suppressions do not block future sends. Internal responses include full email addresses; operators must treat this endpoint as sensitive personal data access.
