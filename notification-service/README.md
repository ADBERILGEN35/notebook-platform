# notification-service

Internal email notification service with provider-agnostic delivery.

## Providers

`EMAIL_PROVIDER` supports `log`, `noop`, `smtp`, `generic-http` and `sendgrid`.

`generic-http` is the first production-oriented HTTP adapter. It posts email JSON to
`EMAIL_GENERIC_HTTP_URL` with `EMAIL_GENERIC_HTTP_API_KEY`. SMTP remains available as a fallback
provider.

## Webhooks

Provider events are accepted at:

```http
POST /webhooks/email/{provider}
```

Webhooks are disabled by default and require HMAC-SHA256 verification when enabled. The endpoint is
public-routable through api-gateway but does not accept user JWT as authentication.
Production webhook mode requires a timestamp header and replay tolerance.

## Suppression

Bounce and complaint events create suppression records. Sending to a suppressed recipient returns
`409 EMAIL_RECIPIENT_SUPPRESSED` and the provider is not called.

Internal suppression operations are available at `/internal/email/suppressions` with service JWT
scopes `internal:notification:suppression:read` and
`internal:notification:suppression:manage`.

Docs:

- [`../docs/notification-service.md`](../docs/notification-service.md)
- [`../docs/email-delivery.md`](../docs/email-delivery.md)
- [`../docs/email-provider-integration.md`](../docs/email-provider-integration.md)
- [`../docs/email-webhooks.md`](../docs/email-webhooks.md)
- [`../docs/email-suppression.md`](../docs/email-suppression.md)
- [`../docs/email-deliverability.md`](../docs/email-deliverability.md)
- [`../docs/email-dns-records.md`](../docs/email-dns-records.md)
- [`../docs/notification-center.md`](../docs/notification-center.md)
