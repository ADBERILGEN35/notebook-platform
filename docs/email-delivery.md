# Email Delivery

Email delivery is provider-agnostic in Faz 24.

## Providers

`EMAIL_PROVIDER` supports:

| Provider | Behavior | Intended use |
| --- | --- | --- |
| `log` | Accepts email and logs metadata without sending. | Local/dev only |
| `noop` | Accepts email without sending or logging body content. | Tests |
| `smtp` | Sends mail through SMTP using Spring Mail. | Production baseline |

Production profile rejects `log` and `noop`. If `EMAIL_PROVIDER=smtp`, `SMTP_PASSWORD` is required
in prod.

SMTP config:

- `SMTP_HOST`
- `SMTP_PORT`
- `SMTP_USERNAME`
- `SMTP_PASSWORD`
- `SMTP_FROM`
- `SMTP_TLS_ENABLED`

## Security Rules

- SMTP username/password are Kubernetes Secret or ExternalSecret values.
- Invitation accept URL is allowed in the email body and notification request, but not audit
  metadata.
- Provider implementations must not log email bodies in production.
- Service JWT private keys are mounted as files and never embedded in `application.yml`.

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
- `secrets.data.smtpUsername`
- `secrets.data.smtpPassword`

ExternalSecret mappings include:

- `smtp-username`
- `smtp-password`
- `workspace-service-jwt-private-key.pem`
- `workspace-service-jwt-public-key.pem`

## Failure Policy

Workspace invitation creation calls notification-service synchronously after the invitation row is
created. If notification-service is unavailable or rejects the request, the workspace transaction is
rolled back and the API returns `503 NOTIFICATION_SERVICE_UNAVAILABLE`.

Future work can replace this with a workspace-service outbox/event-driven handoff to preserve
invitation creation when notification-service is temporarily down.
