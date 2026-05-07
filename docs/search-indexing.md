# Search Indexing

Content-service writes a search index outbox event after note lifecycle changes:

- note create: upsert search document
- note update: upsert search document
- note restore: upsert search document
- note archive: archive search document

The outbox row is stored in the same transaction as the note change. A background worker later calls
search-service. Search-service failures do not fail the note write transaction; they produce retry
or failed outbox state. See [`search-index-outbox.md`](search-index-outbox.md).

## Indexed Fields

- `workspaceId`
- `notebookId`
- `noteId`
- `title`
- searchable plain text extracted from `contentBlocks`
- tag IDs as `tagsText`
- created/updated actor and note timestamps
- `sourceVersion`

`SEARCH_MAX_INDEXED_CHARS` limits extracted body text. The default is `200000`.

## Source Version

`noteId` is unique. If an incoming `sourceVersion` is older than the indexed document version, the
request is ignored and the current document is returned. Same or newer versions update the index.

With OpenSearch enabled, PostgreSQL still makes the source-version decision first. OpenSearch is a
remote projection and does not run a Phase 31 optimistic version script.

## Content Extraction

The extractor supports best-effort text extraction from paragraph, heading, list item, code, quote,
callout, todo and table-like blocks. It recurses through `content`, `props`, `cells` and `children`.
Unknown block types are not rejected by search-service.

## Failure Policy

Faz 26 chooses write availability plus durable indexing events. Search drift should be limited to
events that are still retrying or are in `FAILED` state. Operators can requeue failed events through
the internal reprocess endpoint.

If search-service data is lost or corrupted beyond outbox recovery, run the pull-based reindex
backfill job documented in [`search-reindex-backfill.md`](search-reindex-backfill.md).

Provider migration can enable `SEARCH_DUAL_WRITE_ENABLED=true` before switching queries to
`SEARCH_PROVIDER=opensearch`. Keep `SEARCH_PROVIDER=postgres` as rollback until OpenSearch has been
validated with reindex, archive and permission filtering.

## Faz 34 Permission Snapshot During Indexing

On upsert, search-service fetches notebook snapshot from workspace-service internal API and stores
permission projection in `search_documents`.

If snapshot fetch fails, indexing uses conservative fallback:

- `restricted=true`
- `workspaceReadable=false`
- null version/indexed-at metadata

This keeps authorization fail-closed while preserving indexing availability.
