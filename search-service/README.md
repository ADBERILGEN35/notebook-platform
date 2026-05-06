# search-service

Provider-agnostic search foundation for notes. Faz 25 uses PostgreSQL full-text search and exposes
gateway-protected `GET /search/notes`.

## Indexing

Search-service accepts internal indexing calls only:

- `POST /internal/search/documents`
- `DELETE /internal/search/documents/{noteId}`

Both require content-service service JWT scope `internal:search:index:write` with
`aud=search-service`.

Faz 26 changes the producer side: content-service writes a durable `search_index_outbox` row in the
same transaction as note lifecycle changes, then a worker retries delivery to these endpoints.
Search-service keeps the same idempotent `sourceVersion` behavior and ignores older updates.

## Reindex / Backfill

Faz 27 adds internal reindex jobs:

- `POST /internal/search/reindex-jobs`
- `GET /internal/search/reindex-jobs/{jobId}`
- `POST /internal/search/reindex-jobs/{jobId}/cancel`

Modes are `FULL`, `WORKSPACE` and `NOTEBOOK`. The worker pulls source notes from content-service
`GET /internal/search-index-source/notes` and upserts through the same indexing service.

## Current Limits

- PostgreSQL FTS only; OpenSearch/Elasticsearch provider is future work.
- Orphan cleanup / mark-and-sweep is future work.
- Advanced highlighting, ranking tuning and semantic/vector search are future work.
