# Email Suppression

Faz 32 adds recipient suppression to prevent repeated sends to bounced or complained addresses.

## Domain

`email_suppressions` stores:

- normalized email
- reason: `BOUNCE`, `COMPLAINT`, `MANUAL`, `UNSUBSCRIBE`
- provider and provider event id
- source
- created and optional expiry timestamps

The table enforces uniqueness on `lower(email)`.

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
