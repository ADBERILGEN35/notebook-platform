# Realtime Notifications SSE (Faz 56/57)

## Scope

Faz 56 upgrades in-app notification UX with Server-Sent Events (SSE) for low-latency updates while
keeping the existing polling flow as reliability fallback.

Out of scope in the original Faz 56 phase:

- WebSocket bidirectional collaboration
- External brokers (Kafka/RabbitMQ) for fanout
- Push notifications, PWA offline mode

**Faz 64** adds a **PostgreSQL durable fanout outbox** (see
[`notification-durable-fanout.md`](notification-durable-fanout.md)) while keeping the same SSE event
types and wire format. Delivery remains at-least-once-ish; REST + DB stay authoritative.

## Endpoint

`GET /notifications/stream`

- Auth required via gateway user JWT/cookie flow.
- notification-service trusts gateway user context (`X-User-Id`) and does not accept user id from
  request body/query.
- Returns `text/event-stream` with:
  - `Cache-Control: no-cache`
  - `Connection: keep-alive`
  - `X-Accel-Buffering: no`

## Event Types

- `notification.created`
- `notification.read`
- `notification.archived`
- `notification.unread_count`
- `heartbeat`

Payloads are intentionally minimal and avoid sensitive metadata.

## Frontend Behavior

- SSE is enabled only when:
  - notifications feature is enabled
  - runtime flag `NOTIFICATIONS_SSE_ENABLED` is enabled
  - auth transport is cookie/dual (EventSource cannot send custom Authorization headers)
- Bearer-only mode uses polling fallback only.
- Existing unread polling remains active (30s) to preserve resilience when SSE disconnects or misses
  events.

## Configuration

notification-service:

- `NOTIFICATIONS_SSE_ENABLED` (default `true`)
- `NOTIFICATIONS_SSE_HEARTBEAT_SECONDS` (default `25`)
- `NOTIFICATIONS_SSE_TIMEOUT_SECONDS` (default `0`, no timeout)
- `NOTIFICATIONS_SSE_MAX_CONNECTIONS_PER_USER` (default `5`)

frontend runtime:

- `FRONTEND_NOTIFICATIONS_SSE_ENABLED=true|false`

## Observability

Metrics:

- `notifications_sse_connections_active`
- `notifications_sse_connected_total`
- `notifications_sse_disconnected_total`
- `notifications_sse_events_sent_total{eventType}`
- `notifications_sse_send_failures_total`
- `notifications_sse_connections_rejected_total{reason}`

## Distributed Fanout (Faz 57)

- Redis pub/sub fanout is used to broadcast SSE events between notification-service pods.
- Local-first + self-skip model is used:
  - origin pod delivers immediately to local emitters
  - event is published to Redis
  - subscriber skips self-origin envelopes and delivers foreign-origin events
- Polling fallback is still preserved because Redis pub/sub is best-effort (non-durable).

See [`realtime-notifications-redis-fanout.md`](realtime-notifications-redis-fanout.md).
