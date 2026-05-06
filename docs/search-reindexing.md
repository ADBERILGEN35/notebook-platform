# Search Reindexing

Faz 26 adds outbox replay for failed search indexing events. Faz 27 adds pull-based full
reindex/backfill execution owned by search-service.

## Target Design

Reindex is driven by content-service as the source of truth:

```http
GET /internal/search-index-source/notes?cursor=&size=100
```

The source response includes the same fields used by `POST /internal/search/documents`.
Search-service pages through that source and upserts documents into the index.

## Full Backfill

Use `POST /internal/search/reindex-jobs` to create `FULL`, `WORKSPACE` or `NOTEBOOK` jobs. See
[`search-reindex-backfill.md`](search-reindex-backfill.md).

## Recovery Options

If indexing failures occur:

1. inspect `GET /internal/search-index-outbox/status`
2. requeue failed events with `POST /internal/search-index-outbox/reprocess-failed`
3. monitor `search_outbox_pending`, `search_outbox_failed` and oldest pending age
4. if drift remains, start a scoped or full reindex job in search-service

## Future Requirements

- orphan cleanup / mark-and-sweep
- dry-run mode
- no plaintext query or note body in operator logs
