# Email Webhooks

Faz 32 adds a provider webhook endpoint for delivery lifecycle events.

Endpoint:

```http
POST /webhooks/email/{provider}
```

The endpoint is intended to be routed through api-gateway:

```yaml
/webhooks/email/** -> notification-service
```

It does not require user JWT. It requires provider signature verification.

## Config

- `EMAIL_WEBHOOKS_ENABLED=false`
- `EMAIL_WEBHOOK_PROVIDER=generic-http`
- `EMAIL_WEBHOOK_SECRET`
- `EMAIL_WEBHOOK_SIGNATURE_HEADER=X-Email-Signature`
- `EMAIL_WEBHOOK_TIMESTAMP_HEADER=X-Email-Timestamp`
- `EMAIL_WEBHOOK_TOLERANCE_SECONDS=300`
- `EMAIL_WEBHOOK_ALLOW_NOOP_VERIFIER=false`

Prod requires `EMAIL_WEBHOOK_SECRET` when webhooks are enabled.

## Signature

Generic HMAC verification signs:

```text
<timestamp>.<raw-body>
```

with HMAC-SHA256. Header value may be either raw hex or `sha256=<hex>`.

If the timestamp header is present, replay protection rejects events outside the tolerance window. If a provider does not send a timestamp, replay protection is weaker and this must be accepted explicitly in the provider runbook.

## Events

Normalized event types:

- `DELIVERED`
- `BOUNCE`
- `COMPLAINT`
- `UNKNOWN`

Duplicate provider events are idempotent by `provider + providerEventId`.

Unknown events are stored/audited but do not change notification status.
