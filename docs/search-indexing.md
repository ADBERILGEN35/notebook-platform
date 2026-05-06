# Search Indexing

Content-service calls search-service after note lifecycle changes:

- note create: upsert search document
- note update: upsert search document
- note restore: upsert search document
- note archive: archive search document

The call is best-effort. Search-service failures do not fail the note write transaction. Content
audit records `SEARCH_INDEXING_FAILED` with operation and error class so operators can reindex later.

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

## Content Extraction

The extractor supports best-effort text extraction from paragraph, heading, list item, code, quote,
callout, todo and table-like blocks. It recurses through `content`, `props`, `cells` and `children`.
Unknown block types are not rejected by search-service.

## Failure Policy

Faz 25 intentionally chooses write availability over strict search consistency. Search drift is
expected to be repaired by reindex/backfill operations rather than failing user note writes.
