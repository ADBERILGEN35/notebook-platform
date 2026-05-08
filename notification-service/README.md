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
- [`../docs/notification-preferences.md`](../docs/notification-preferences.md)
- [`../docs/workspace-notification-preferences.md`](../docs/workspace-notification-preferences.md)
- [`../docs/realtime-notifications-sse.md`](../docs/realtime-notifications-sse.md)
- [`../docs/realtime-notifications-redis-fanout.md`](../docs/realtime-notifications-redis-fanout.md)
- [`../docs/notification-digest-quiet-hours.md`](../docs/notification-digest-quiet-hours.md)

## SSE runtime flags (Faz 56)

- `NOTIFICATIONS_SSE_ENABLED=true|false`
- `NOTIFICATIONS_SSE_HEARTBEAT_SECONDS` (default `25`)
- `NOTIFICATIONS_SSE_TIMEOUT_SECONDS` (default `0`)
- `NOTIFICATIONS_SSE_MAX_CONNECTIONS_PER_USER` (default `5`)
- `NOTIFICATIONS_SSE_DISTRIBUTED_ENABLED` (default `false`)
- `NOTIFICATIONS_SSE_DISTRIBUTED_PROVIDER` (default `redis`)
- `NOTIFICATIONS_SSE_REDIS_CHANNEL` (default `notification:sse:events`)
- `NOTIFICATIONS_SSE_DISTRIBUTED_PUBLISH_LOCAL_FIRST` (default `true`)
- `NOTIFICATION_INSTANCE_ID` (optional; defaults to hostname-random)
- `NOTIFICATION_DIGEST_ENABLED`
- `NOTIFICATION_DIGEST_WORKER_ENABLED`
- `NOTIFICATION_DIGEST_POLL_INTERVAL_SECONDS`
- `NOTIFICATION_DIGEST_BATCH_SIZE`
- `NOTIFICATION_DIGEST_MAX_ITEMS_PER_EMAIL`
- `NOTIFICATION_DIGEST_DAILY_SEND_TIME`
- `NOTIFICATION_DIGEST_WEEKLY_DAY`
- `NOTIFICATION_DIGEST_WEEKLY_SEND_TIME`

## Internal admin status (Faz 63)

- `GET /internal/admin/status/notification` — service JWT with scope `internal:admin:status:read`, opt-in via `NOTIFICATION_INTERNAL_ADMIN_STATUS_ENABLED`.
- Returns configuration-class flags only (no email API keys or webhook secrets). See `docs/enterprise-admin-console.md`.

## Durable SSE fanout outbox (Faz 64)

- `NOTIFICATION_FANOUT_OUTBOX_ENABLED`, `NOTIFICATION_FANOUT_WORKER_ENABLED`, `NOTIFICATION_FANOUT_IMMEDIATE_LOCAL_DELIVERY`, poll/batch/retry/backoff/lock envs — see `docs/notification-durable-fanout.md`.
- Table `notification_fanout_outbox`; ops SQL examples under `scripts/notifications/`.
