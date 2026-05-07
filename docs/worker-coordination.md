# Worker Coordination

Faz 30 standardizes DB-backed worker coordination for the current scheduled workers. This remains
PostgreSQL-based and does not introduce Kafka, RabbitMQ, Redis locks or Kubernetes Lease objects.

## Inventory

| Service | Worker | Table | Purpose | Claim | Retry | Stale recovery | Multi-pod safety |
| --- | --- | --- | --- | --- | --- | --- | --- |
| notification-service | `EmailWorker` | `email_notifications` | Send queued operational email | `PENDING` due rows with `FOR UPDATE SKIP LOCKED` | exponential backoff to `FAILED` | expired `SENDING` lease returns to `PENDING` | yes, row lease + idempotency key |
| content-service | `SearchIndexOutboxWorker` | `search_index_outbox` | Dispatch note indexing events | `PENDING` due rows with `FOR UPDATE SKIP LOCKED` | exponential backoff to `FAILED` | expired `PROCESSING` lease returns to `PENDING` retry path | yes, row lease + idempotency key |
| search-service | `SearchReindexWorker` | `search_reindex_jobs` | Pull source notes and rebuild search index | one `PENDING` job with `FOR UPDATE SKIP LOCKED` | job-level fail, operator restart | expired `RUNNING` lease is marked `FAILED` | yes, single active job rule + lease |

## Standard

- Claim happens inside a transaction.
- Queue-style workers use `FOR UPDATE SKIP LOCKED`.
- Claimed rows store `lockedBy`, `lockedAt` and `lockExpiresAt`.
- Long-running reindex jobs store `heartbeatAt`.
- Expired queue leases are recovered before new rows are claimed.
- Expired reindex jobs are failed, not resumed by another pod.
- Worker instance id is visible in audit metadata and metrics context. Kubernetes deployments set
  `WORKER_INSTANCE_ID` from pod name; local runs generate `hostname-worker-random`.
- Batch size, poll interval and lock timeout are config-driven.
- Workers stop accepting new claims during Spring shutdown. Current in-flight work finishes through
  its normal transaction boundary.
- Processing must stay idempotent because a pod can crash after a provider/API side effect and
  before marking the row complete.

## Config

notification-service:

- `EMAIL_WORKER_ENABLED`
- `EMAIL_WORKER_BATCH_SIZE`
- `EMAIL_WORKER_LOCK_TIMEOUT_SECONDS`
- `WORKER_INSTANCE_ID`

content-service:

- `SEARCH_OUTBOX_WORKER_ENABLED`
- `SEARCH_OUTBOX_BATCH_SIZE`
- `SEARCH_OUTBOX_LOCK_TIMEOUT_SECONDS`
- `WORKER_INSTANCE_ID`

search-service:

- `SEARCH_REINDEX_WORKER_ENABLED`
- `SEARCH_REINDEX_BATCH_SIZE`
- `SEARCH_REINDEX_LOCK_TIMEOUT_SECONDS`
- `SEARCH_REINDEX_HEARTBEAT_INTERVAL_SECONDS`
- `WORKER_INSTANCE_ID`

## Operations

- Keep `terminationGracePeriodSeconds` longer than typical worker batch duration.
- Use HPA cautiously for worker-owning services; extra pods are safe but can increase provider/API
  pressure.
- Alert when queue depth grows while claimed counters remain flat.
- Investigate stale recovery spikes; they usually indicate pod restarts, provider latency or lock
  timeout values that are too low.
- Reindex jobs are intentionally conservative: stale `RUNNING` jobs fail and require a fresh
  operator-created job because reindex is idempotent and restartable.
