# Search Index Outbox

Faz 26 moves content-service to a DB-backed outbox for search indexing. Note writes no longer call
search-service synchronously. They write a `search_index_outbox` row in the same transaction as the
note change, and a scheduled worker dispatches the row to search-service internal indexing APIs.

## Event Model

`search_index_outbox` stores:

- `NOTE_UPSERT` for note create, update and restore
- `NOTE_ARCHIVE` for note archive
- `PENDING`, `PROCESSING`, `PROCESSED`, `FAILED` and `CANCELLED` states
- JSON payload for upsert requests; archive events keep only note identity
- unique `idempotency_key`

Idempotency keys use `search-index:{noteId}:{sourceVersion}:{eventType}` for upserts. Archive
events use the note update timestamp as the version segment because archive does not create a new
note version row.

## Worker

The worker claims due `PENDING` events with `FOR UPDATE SKIP LOCKED`, marks them `PROCESSING`, then
dispatches outside the note write path:

- `NOTE_UPSERT` -> `POST /internal/search/documents`
- `NOTE_ARCHIVE` -> `DELETE /internal/search/documents/{noteId}`

Failures are retried with exponential backoff. After `SEARCH_OUTBOX_MAX_ATTEMPTS`, the event moves
to `FAILED` and waits for operator reprocess.

Claimed rows store `lockedBy`, `lockedAt` and `lockExpiresAt`. If a pod dies after marking an event
`PROCESSING`, later workers recover expired locks after `SEARCH_OUTBOX_LOCK_TIMEOUT_SECONDS` and put
the event back into the retry path.

Config:

- `SEARCH_OUTBOX_WORKER_ENABLED`
- `SEARCH_OUTBOX_BATCH_SIZE`
- `SEARCH_OUTBOX_MAX_ATTEMPTS`
- `SEARCH_OUTBOX_INITIAL_DELAY_SECONDS`
- `SEARCH_OUTBOX_MAX_DELAY_SECONDS`
- `SEARCH_OUTBOX_POLL_INTERVAL_SECONDS`
- `SEARCH_OUTBOX_LOCK_TIMEOUT_SECONDS`
- `WORKER_INSTANCE_ID`

## Ops API

These endpoints are internal only and are not routed through api-gateway:

```http
GET /internal/search-index-outbox/status
POST /internal/search-index-outbox/reprocess-failed?workspaceId=&noteId=&limit=
```

Both require `X-Service-Authorization: Bearer <service-jwt>` with `token_type=service` and
`aud=content-service`.

Scopes:

- `internal:content:search-outbox:read`
- `internal:content:search-outbox:manage`

## Observability

Metrics:

- `search_outbox_pending`
- `search_outbox_failed`
- `search_outbox_processed_total`
- `search_outbox_failed_total`
- `search_outbox_retry_total`
- `search_outbox_processing_duration`
- `search_outbox_oldest_pending_age`

Audit events:

- `SEARCH_INDEX_OUTBOX_QUEUED`
- `SEARCH_INDEX_OUTBOX_PROCESSED`
- `SEARCH_INDEX_OUTBOX_RETRY_SCHEDULED`
- `SEARCH_INDEX_OUTBOX_FAILED`
- `SEARCH_INDEX_OUTBOX_STALE_RECOVERED`

Audit metadata intentionally excludes note body and search payload. It includes note/workspace IDs,
event type, attempt count and sanitized error class/message only.

## Failure Policy

Note create/update/restore/archive success is independent from search-service availability. Search
consistency is eventual. If the worker cannot reach search-service, events stay retryable or move to
`FAILED` for reprocess.

Faz 31 does not change the content-service outbox contract. Outbox delivery still targets
search-service indexing endpoints; search-service then decides whether accepted writes are only kept
in PostgreSQL canonical state or also projected to OpenSearch.
