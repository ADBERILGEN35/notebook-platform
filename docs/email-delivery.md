# Email Delivery

Email delivery is provider-agnostic. Faz 32 adds a generic HTTP provider, webhook event handling and
suppression list behavior.

## Providers

`EMAIL_PROVIDER` supports:

| Provider | Behavior | Intended use |
| --- | --- | --- |
| `log` | Accepts email and logs metadata without sending. | Local/dev only |
| `noop` | Accepts email without sending or logging body content. | Tests |
| `smtp` | Sends mail through SMTP using Spring Mail. | Production baseline |
| `generic-http` | Sends through a provider-compatible HTTPS API. | Managed email providers |
| `sendgrid` | Alias to the generic HTTP adapter in Faz 32. | SendGrid-compatible rollout |

Production profile rejects `log` and `noop`. If `EMAIL_PROVIDER=smtp`, `SMTP_PASSWORD` is required
in prod.
If `EMAIL_PROVIDER=generic-http` or `sendgrid`, `EMAIL_GENERIC_HTTP_URL` and
`EMAIL_GENERIC_HTTP_API_KEY` are required in prod.

SMTP config:

- `SMTP_HOST`
- `SMTP_PORT`
- `SMTP_USERNAME`
- `SMTP_PASSWORD`
- `SMTP_FROM`
- `SMTP_TLS_ENABLED`
- `EMAIL_GENERIC_HTTP_URL`
- `EMAIL_GENERIC_HTTP_API_KEY`
- `EMAIL_WEBHOOKS_ENABLED`
- `EMAIL_WEBHOOK_SECRET`

## Security Rules

- SMTP username/password are Kubernetes Secret or ExternalSecret values.
- Invitation accept URL is allowed in the email body and notification request, but not audit
  metadata.
- Provider implementations must not log email bodies in production.
- Service JWT private keys are mounted as files and never embedded in `application.yml`.
- Webhook signatures are required when webhooks are enabled.
- Provider payloads are sanitized before persistence/audit.

## Deployment

In Kubernetes, notification-service is ClusterIP-only. api-gateway does not expose it through
Ingress. NetworkPolicy allows platform-internal pods to call it; public ingress reaches only
api-gateway.

Helm values expose:

- `config.emailProvider`
- `config.smtpHost`
- `config.smtpPort`
- `config.smtpFrom`
- `config.smtpTlsEnabled`
- `config.emailWebhooksEnabled`
- `config.emailGenericHttpUrl`
- `secrets.data.smtpUsername`
- `secrets.data.smtpPassword`
- `secrets.data.emailProviderApiKey`
- `secrets.data.emailWebhookSecret`

ExternalSecret mappings include:

- `smtp-username`
- `smtp-password`
- `email-provider-api-key`
- `email-webhook-secret`
- `workspace-service-jwt-private-key.pem`
- `workspace-service-jwt-public-key.pem`

## Failure Policy

Workspace invitation creation calls notification-service synchronously after the invitation row is
created. If notification-service is unavailable or rejects the request, the workspace transaction is
rolled back and the API returns `503 NOTIFICATION_SERVICE_UNAVAILABLE`.

If the recipient is suppressed, notification-service returns `409 EMAIL_RECIPIENT_SUPPRESSED` and
workspace-service maps it to `409 NOTIFICATION_RECIPIENT_SUPPRESSED`.

Future work can replace this with a workspace-service outbox/event-driven handoff to preserve
invitation creation when notification-service is temporarily down.
