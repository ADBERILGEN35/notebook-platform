# Search Reindexing

Faz 25 does not implement full reindex/backfill execution. It defines the operational direction and
keeps the API surface small.

## Target Design

Future reindex should be driven by content-service as the source of truth:

```http
GET /internal/notes/search-index-source?page=0&size=100
```

The source response should include the same fields used by
`POST /internal/search/documents`. Search-service or an operator script can page through that source
and upsert documents into the index.

## Why Not Implement Full Reindex Now

Pulling all notes from search-service would require new content-service internal APIs, paging
contracts, service auth, rate limiting and operational safeguards. That expands the phase beyond the
foundation goal.

## Manual Recovery For Now

If indexing failures occur:

1. inspect content-service audit events for `SEARCH_INDEXING_FAILED`
2. identify affected note IDs and workspace IDs
3. re-save or restore the note through a controlled backend operation, or add the future source
   endpoint before production backfill

## Future Requirements

- guarded operator script
- bounded page size
- dry-run mode
- service JWT auth
- progress report with indexed, skipped, failed counts
- no plaintext query or note body in operator logs
