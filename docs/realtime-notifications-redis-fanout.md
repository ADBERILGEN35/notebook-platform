# Realtime Notifications Redis Fanout (Faz 57)

## Goal

Faz 57 removes the single-pod SSE fanout limitation by introducing Redis pub/sub distribution across
`notification-service` pods.

## Channel And Envelope

- Redis channel: `notification:sse:events`
- Envelope:
  - `eventId`
  - `originInstanceId`
  - `recipientUserId`
  - `eventType`
  - `payload`
  - `createdAt`

## Delivery Model

- Local-first delivery: origin pod pushes to local SSE emitters immediately.
- The same event is published to Redis.
- All pods subscribe and attempt local delivery.
- Self-echo is skipped by matching `originInstanceId`.
- Redis pub/sub alone is best-effort and non-durable.

**Faz 81:** Publish success/failure and subscriber-received events are counted into privacy-safe hourly aggregates for the admin dashboard ([`notification-analytics-dashboard.md`](notification-analytics-dashboard.md)).

**Faz 64**: notification-service can persist fanout events to `notification_fanout_outbox` before
publishing; a worker retries Redis (and local-first) publish until success or dead-letter. Redis remains
the realtime transport; the outbox adds durability across process and transient Redis issues. See
[`notification-durable-fanout.md`](notification-durable-fanout.md). Source of truth remains DB + REST API.

## Config

- `NOTIFICATION_INSTANCE_ID` (optional, defaults to hostname-random)
- `NOTIFICATIONS_SSE_DISTRIBUTED_ENABLED`
- `NOTIFICATIONS_SSE_DISTRIBUTED_PROVIDER=redis`
- `NOTIFICATIONS_SSE_REDIS_CHANNEL`
- `NOTIFICATIONS_SSE_DISTRIBUTED_PUBLISH_LOCAL_FIRST`
- `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`, `REDIS_SSL_ENABLED`

## Failure Behavior

- Redis publish failures do not fail notification create/read/archive workflows.
- Invalid subscriber messages are dropped and metered.
- Polling fallback remains active for UX correctness during disconnects or message loss.

## Observability

- `notifications_sse_distributed_published_total{status}`
- `notifications_sse_distributed_received_total`
- `notifications_sse_distributed_delivered_total`
- `notifications_sse_distributed_skipped_self_total`
- `notifications_sse_distributed_invalid_messages_total`
- `notifications_sse_distributed_publish_failures_total`
- `notifications_sse_distributed_subscriber_errors_total`
