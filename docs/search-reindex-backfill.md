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

Request:

```json
{
  "mode": "WORKSPACE",
  "workspaceId": "00000000-0000-0000-0000-000000000000",
  "notebookId": null,
  "cleanupOrphans": false
}
```

## Worker

The scheduled worker claims a pending job with `FOR UPDATE SKIP LOCKED`, marks it `RUNNING`, then
pulls batches from content-service. Each source item is converted to the existing
`IndexDocumentRequest` and sent through the existing upsert path.

State transitions:

- `PENDING` -> `RUNNING`
- `RUNNING` -> `COMPLETED`
- `RUNNING` -> `FAILED`
- `PENDING`/`RUNNING` -> `CANCELLED`

## Mark And Sweep Cleanup

Faz 28 adds an opt-in mark-and-sweep phase. `search_documents` stores:

- `last_seen_reindex_job_id`
- `last_seen_reindex_at`

Every document seen during a reindex job is marked with the current job id. After a successful full
scan, cleanup can archive active documents in the job scope that were not seen by the job. Cleanup
never hard-deletes documents.

Cleanup runs only when both conditions are true:

- `SEARCH_REINDEX_ORPHAN_CLEANUP_ENABLED=true`
- request `cleanupOrphans=true`

If either is false, cleanup is skipped and the job can still complete. Cancelled and failed jobs do
not run cleanup because their source scan may be incomplete.

Scope rules:

- `FULL`: all active search documents
- `WORKSPACE`: active documents in `workspaceId`
- `NOTEBOOK`: active documents in `workspaceId + notebookId`

If an archived source note is seen, it remains archived in search. If an active source note is seen
for an archived search document, the upsert clears `archivedAt` and restores it to active search
eligibility.

## Consistency

`SearchDocument.noteId` remains unique. Existing source-version behavior remains in force:

- older incoming `sourceVersion` is ignored
- same or newer `sourceVersion` upserts the document
- archived source notes remain searchable only as archived documents and are excluded from public
  search results

Documents present in search-service but absent from content-service are archived only when
mark-and-sweep cleanup is enabled and requested. Hard delete remains out of scope.

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
- `search_reindex_orphans_archived_total`
- `search_reindex_cleanup_duration`
- `search_reindex_cleanup_skipped_total`

Audit events:

- `SEARCH_REINDEX_JOB_CREATED`
- `SEARCH_REINDEX_JOB_STARTED`
- `SEARCH_REINDEX_JOB_COMPLETED`
- `SEARCH_REINDEX_JOB_FAILED`
- `SEARCH_REINDEX_JOB_CANCELLED`
- `SEARCH_REINDEX_CLEANUP_STARTED`
- `SEARCH_REINDEX_CLEANUP_COMPLETED`
- `SEARCH_REINDEX_CLEANUP_SKIPPED`

Audit metadata does not include note body or extracted content.
