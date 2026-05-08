# Durable notification fanout (Faz 64)

## Goal

SSE notification events (`notification.created`, `notification.read`, `notification.archived`,
`notification.unread_count`) are fanned out to connected browsers via in-process emitters and,
optionally, Redis pub/sub for multi-instance delivery. Redis pub/sub alone is **not durable**:
subscribers that are down or network partitions can miss messages.

Faz 64 adds a **PostgreSQL-backed fanout outbox** so each logical SSE event is persisted before
(or alongside) realtime publish. A worker claims pending rows, publishes through the existing
`NotificationSseEventDispatcher` (Redis + local-first rules unchanged), and marks rows `SENT`
or retries with exponential backoff, eventually `DEAD`.

## Why DB outbox (not Kafka/RabbitMQ in this phase)

- Reuses the notification service database and operational model.
- Sufficient for low/medium scale and avoids new broker infrastructure.
- Kafka/RabbitMQ remain future work for higher throughput and cross-service fanout.

## Delivery semantics

- **At-least-once-ish** for realtime SSE: duplicates are possible when:
  - immediate local delivery and worker both publish the same `eventId`;
  - Redis retries; clients reconnect.
- **Source of truth** for notification list and unread counts remains the HTTP APIs and DB
  (`user_notifications`), not the event stream.
- Clients must treat SSE handlers as **idempotent** (invalidate queries, set unread count to last
  value).

## Configuration

| Env / property | Meaning |
|----------------|---------|
| `NOTIFICATION_FANOUT_OUTBOX_ENABLED` | Persist fanout rows on in-app mutations (default `false`). |
| `NOTIFICATION_FANOUT_WORKER_ENABLED` | Poll and publish pending rows (default `true` when outbox on in Helm). |
| `NOTIFICATION_FANOUT_IMMEDIATE_LOCAL_DELIVERY` | After writing outbox, still call realtime dispatch in the request thread (default `true`). |
| `NOTIFICATION_FANOUT_POLL_INTERVAL_SECONDS` | Worker fixed delay between polls. |
| `NOTIFICATION_FANOUT_BATCH_SIZE` | `FOR UPDATE SKIP LOCKED` batch. |
| `NOTIFICATION_FANOUT_MAX_ATTEMPTS` | Publish attempts before `DEAD`. |
| `NOTIFICATION_FANOUT_BACKOFF_*` | Exponential backoff between retries. |
| `NOTIFICATION_FANOUT_LOCK_TTL_SECONDS` | Sending lock expiry; stale locks return to `PENDING`. |
| `NOTIFICATION_FANOUT_SENT_RETENTION_HOURS` | Documentation / future purge (no automatic purge in Faz 64). |
| `NOTIFICATION_FANOUT_DEAD_RETENTION_DAYS` | Documentation / ops retention guidance. |

## Request path vs worker

1. **Transactional**: `user_notifications` change + optional `notification_fanout_outbox` insert
   share the same transaction.
2. **Immediate path** (if enabled): `dispatchAllowDistributedFailure` runs after the insert.
   Distributed publish failures are **swallowed** so the HTTP request still commits; the worker
   retries from outbox.
3. **Worker**: `dispatchStrictDistributed` surfaces Redis failures so retry/dead-letter logic runs.

## Security / privacy

Outbox `payload` mirrors the existing minimal SSE payload (ids, types, counts, timestamps). No
tokens, cookies, or email bodies. `recipient_user_id` is used only for routing.

## Metrics

- `notifications_fanout_outbox_pending` (gauge)
- `notifications_fanout_outbox_dead` (gauge)
- `notifications_fanout_outbox_claimed_total`
- `notifications_fanout_outbox_published_total` (`result`: `sent`, `retry_scheduled`, `dead`)
- `notifications_fanout_outbox_retry_total`
- `notifications_fanout_outbox_dead_total`
- `notifications_fanout_outbox_stale_lock_recovered_total`
- `notifications_fanout_outbox_duration_seconds`
- `notifications_fanout_immediate_failures_total`

**Alert ideas**: sustained growth of `pending` or `dead`; elevated `retry_total`; worker stopped
(process up but `claimed_total` flat while pending &gt; 0).

## Rollout / rollback

1. Deploy migration + code with `NOTIFICATION_FANOUT_OUTBOX_ENABLED=false`.
2. Enable outbox in staging; keep `IMMEDIATE_LOCAL_DELIVERY=true`.
3. Enable worker; watch metrics.
4. Optionally set `IMMEDIATE_LOCAL_DELIVERY=false` to reduce duplicate SSE events (slight latency).
5. Production: enable gradually per environment.

**Rollback**: set `OUTBOX_ENABLED=false` and optionally `WORKER_ENABLED=false`. Existing SSE +
polling behavior continues.

## Dead letter ops

See:

- `scripts/notifications/list-dead-fanout-events.sql.example`
- `scripts/notifications/requeue-dead-fanout-events.sql.example`

## Related docs

- [`realtime-notifications-sse.md`](realtime-notifications-sse.md)
- [`realtime-notifications-redis-fanout.md`](realtime-notifications-redis-fanout.md)
- [`notification-service.md`](notification-service.md)
