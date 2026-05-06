# Search Reindex Backfill

Faz 27 implements pull-based full reindex/backfill. Search-service owns job state and pulls source
notes from content-service through an internal read API.

## Strategy

Chosen model: search-service pull.

- content-service exposes source-of-truth note batches
- search-service stores `search_reindex_jobs`
- search-service worker pulls pages and uses the existing idempotent indexing path

This keeps search lifecycle ownership inside search-service and avoids generating synthetic outbox
events for historical data.

## Modes

- `FULL`: scan all notes
- `WORKSPACE`: scan one workspace
- `NOTEBOOK`: scan one notebook inside one workspace

Only one `PENDING` or `RUNNING` job is accepted at a time. A second create request returns
`409 REINDEX_JOB_ALREADY_RUNNING`.

## Source API

Content-service internal endpoint:

```http
GET /internal/search-index-source/notes?workspaceId=&notebookId=&cursor=&size=100
```

Auth:

- `X-Service-Authorization: Bearer <service-jwt>`
- `aud=content-service`
- scope `internal:content:search-index-source:read`

Response includes archived notes with `archivedAt`, because reindex must preserve archive state in
the search index. The cursor is opaque and based on `(noteUpdatedAt, noteId)`.

## Reindex Job API

Search-service internal endpoints:

```http
POST /internal/search/reindex-jobs
GET /internal/search/reindex-jobs/{jobId}
POST /internal/search/reindex-jobs/{jobId}/cancel
```

Auth:

- `X-Service-Authorization: Bearer <service-jwt>`
- `aud=search-service`
- scope `internal:search:reindex:manage`

## Worker

The scheduled worker claims a pending job with `FOR UPDATE SKIP LOCKED`, marks it `RUNNING`, then
pulls batches from content-service. Each source item is converted to the existing
`IndexDocumentRequest` and sent through `SearchIndexService.upsert`.

State transitions:

- `PENDING` -> `RUNNING`
- `RUNNING` -> `COMPLETED`
- `RUNNING` -> `FAILED`
- `PENDING`/`RUNNING` -> `CANCELLED`

## Consistency

`SearchDocument.noteId` remains unique. Existing source-version behavior remains in force:

- older incoming `sourceVersion` is ignored
- same or newer `sourceVersion` upserts the document
- archived source notes remain searchable only as archived documents and are excluded from public
  search results

MVP does not perform orphan cleanup. Documents present in search-service but absent from
content-service are not hard-deleted or archived by this phase.

## Observability

Metrics:

- `search_reindex_jobs_total`
- `search_reindex_running`
- `search_reindex_completed_total`
- `search_reindex_failed_total`
- `search_reindex_scanned_total`
- `search_reindex_indexed_total`
- `search_reindex_duration`
- `search_reindex_last_run_timestamp`

Audit events:

- `SEARCH_REINDEX_JOB_CREATED`
- `SEARCH_REINDEX_JOB_STARTED`
- `SEARCH_REINDEX_JOB_COMPLETED`
- `SEARCH_REINDEX_JOB_FAILED`
- `SEARCH_REINDEX_JOB_CANCELLED`

Audit metadata does not include note body or extracted content.
