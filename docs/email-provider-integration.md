# Email Provider Integration

Faz 32 keeps notification-service provider-agnostic and adds a production-oriented HTTP provider path without binding the platform to a real cloud account.

## Providers

Supported provider values:

- `log`
- `noop`
- `smtp`
- `generic-http`
- `sendgrid`

`sendgrid` currently uses the same generic HTTP adapter contract. A provider-specific SendGrid SDK, Mailgun adapter or AWS SES/SNS integration is future work.

## Generic HTTP Provider

Config:

- `EMAIL_PROVIDER=generic-http`
- `EMAIL_GENERIC_HTTP_URL`
- `EMAIL_GENERIC_HTTP_API_KEY`
- `EMAIL_GENERIC_HTTP_AUTHORIZATION_HEADER=Authorization`
- `EMAIL_REPLY_TO` optional monitored reply address
- `EMAIL_GENERIC_HTTP_CONNECT_TIMEOUT_MS=1000`
- `EMAIL_GENERIC_HTTP_REQUEST_TIMEOUT_MS=3000`

The adapter posts a JSON email request to the configured URL. It accepts a JSON response with optional `messageId` or `id` and optional `status`. Raw provider response is sanitized before it is stored or surfaced to logs/audit.

Production fail-fast:

- `EMAIL_PROVIDER=log` and `EMAIL_PROVIDER=noop` are rejected in prod.
- `EMAIL_PROVIDER=smtp` requires `SMTP_PASSWORD`.
- `EMAIL_PROVIDER=generic-http|sendgrid` requires URL and API key.

SMTP remains a fallback provider. The generic HTTP provider is intended for managed providers that expose HTTPS send APIs.

## Production Readiness

Before switching production traffic to a real provider:

- publish provider-specific SPF/DKIM/DMARC records from [`email-dns-records.md`](email-dns-records.md);
- complete the domain checklist in [`email-deliverability.md`](email-deliverability.md);
- run `scripts/email/provider-readiness-check.sh` in staging;
- enable signed delivery/bounce/complaint webhooks with timestamp replay protection.

## Identity Security Email

`identity-service` can request notification-service after `POST /auth/revoke-all`.

Config:

- `IDENTITY_SECURITY_NOTIFICATIONS_ENABLED=true`
- `NOTIFICATION_SERVICE_URL`
- `IDENTITY_SERVICE_JWT_PRIVATE_KEY_PATH`
- `IDENTITY_SERVICE_JWT_AUDIENCE=notification-service`

Failure policy is fail-open for revoke-all: token revocation succeeds even if notification-service is unavailable. Identity records audit/log events for requested or failed security email notifications.
