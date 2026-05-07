# search-service

Provider-agnostic search foundation for notes. PostgreSQL full-text search remains the default.
Faz 31 adds OpenSearch projection/query support while preserving PostgreSQL as canonical local
index state and fallback.

## Provider Config

- `SEARCH_PROVIDER=postgres|opensearch`
- `SEARCH_DUAL_WRITE_ENABLED=false`
- `SEARCH_FALLBACK_TO_POSTGRES=false`
- `OPENSEARCH_URL`
- `OPENSEARCH_USERNAME`
- `OPENSEARCH_PASSWORD`
- `OPENSEARCH_INDEX_NOTES=notebook-notes`

Use [`../docs/opensearch-provider.md`](../docs/opensearch-provider.md) for mapping, rollout and
rollback.

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
- `GET /internal/search/reindex-jobs/{jobId}/orphan-preview`

Modes are `FULL`, `WORKSPACE` and `NOTEBOOK`. The worker pulls source notes from content-service
`GET /internal/search-index-source/notes` and upserts through the same indexing service.
`cleanupOrphans=true,dryRunCleanup=true` counts and previews unseen active documents without
modifying `search_documents`. Real cleanup archives unseen active documents only when
`cleanupOrphans=true,dryRunCleanup=false` and `SEARCH_REINDEX_ORPHAN_CLEANUP_ENABLED=true`; hard
delete is never used.

The reindex worker uses a DB lease with `lockedBy`, `lockExpiresAt` and `heartbeatAt`. Expired
`RUNNING` jobs are marked `FAILED` and can be restarted by an operator.

## Current Limits

- Production real cleanup rollout is future work.
- Advanced highlighting, ranking tuning and semantic/vector search are future work.
- Permission-aware exact total count for OpenSearch is future work.
