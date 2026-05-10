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

## Delivery analytics (Faz 81)

- `NOTIFICATION_ANALYTICS_ENABLED`, `NOTIFICATION_ANALYTICS_RETENTION_DAYS`, `NOTIFICATION_ANALYTICS_MAX_RANGE_DAYS`, `NOTIFICATION_ANALYTICS_BUCKET`
- `NOTIFICATION_INTERNAL_ADMIN_ANALYTICS_ENABLED` — enables `GET /internal/admin/notifications/analytics/summary` (service JWT scope `internal:admin:notifications:analytics:read`)
- Docs: `docs/notification-analytics-dashboard.md`, `docs/notification-analytics-privacy.md`

## Dead-letter admin (Faz 82)

- `NOTIFICATION_DEAD_LETTER_ADMIN_ENABLED`, `NOTIFICATION_DEAD_LETTER_MAX_REQUEUE_COUNT`, `NOTIFICATION_DEAD_LETTER_PAGE_MAX_SIZE`, `NOTIFICATION_DEAD_LETTER_RECIPIENT_HASH_PEPPER`
- Internal: `GET/POST /internal/admin/notifications/dead-letter` (+ requeue paths); scopes `internal:admin:notifications:dead-letter:read|requeue`
- Doc: `docs/notification-dead-letter-requeue.md`

## Retention admin / worker (Faz 83)

- `NOTIFICATION_RETENTION_ADMIN_API_ENABLED`, `NOTIFICATION_RETENTION_WORKER_ENABLED`, `NOTIFICATION_RETENTION_DRY_RUN_ONLY`, `NOTIFICATION_RETENTION_MANUAL_RUN_ENABLED`, batch/max-delete/poll envs, digest/email/requeue-request retention days
- Internal: `GET /internal/admin/notifications/retention/plan`, `POST .../run`; scopes `internal:admin:notifications:retention:read|run`
- Internal (Faz 84): `GET/POST /internal/admin/notifications/legal-holds`, `POST .../{id}/release`; scopes `internal:admin:notifications:legal-hold:read|write` (flags `NOTIFICATION_LEGAL_HOLD_*`)
- Docs: `docs/notification-retention-worker.md`, `docs/notification-retention-policy.md`
