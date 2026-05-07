# Search Service

Faz 25 introduced `search-service` with PostgreSQL full-text search. Faz 31 adds the provider
abstraction and OpenSearch projection support while keeping PostgreSQL as the default and canonical
local index state.

## Responsibilities

- store searchable note documents in `search_documents`
- accept internal indexing requests from content-service
- serve protected note search through api-gateway at `GET /search/notes`
- filter by workspace and notebook permission before returning results
- keep search query text out of audit metadata

## APIs

Public, gateway-routed endpoint:

```http
GET /search/notes?workspaceId=<uuid>&q=<query>&notebookId=<uuid>&page=0&size=20
```

`X-User-Id` is required from api-gateway. If `X-Workspace-Id` is present and does not match
`workspaceId`, search-service returns `400 INVALID_WORKSPACE_CONTEXT`.

Internal endpoint:

```http
POST /internal/search/documents
DELETE /internal/search/documents/{noteId}
```

Internal indexing requires `X-Service-Authorization: Bearer <service-jwt>` with:

- `token_type=service`
- `iss=content-service`
- `aud=search-service`
- `scope=internal:search:index:write`

Reindex ops endpoints are internal only and require service JWT scope
`internal:search:reindex:manage`:

```http
POST /internal/search/reindex-jobs
GET /internal/search/reindex-jobs/{jobId}
POST /internal/search/reindex-jobs/{jobId}/cancel
GET /internal/search/reindex-jobs/{jobId}/orphan-preview
```

`cleanupOrphans=true,dryRunCleanup=true` previews orphan cleanup without modifying documents.
Preview samples return only ids and timestamps, not titles or note content.

## PostgreSQL FTS

`search_documents.search_vector` is a generated `tsvector`:

- title: weight `A`
- tags and notebook name: weight `B`
- content text: weight `C`

The index uses PostgreSQL `simple` config. Turkish and English stemming are future work because
portable PostgreSQL language configuration differs by installation.

## Provider Architecture

`SEARCH_PROVIDER=postgres|opensearch` selects the query provider. `SEARCH_DUAL_WRITE_ENABLED=true`
projects accepted index writes to OpenSearch for migration. `SEARCH_FALLBACK_TO_POSTGRES=false` is
the default because result consistency is preferred over silent fallback; staging may enable it
during rollout.

See [`opensearch-provider.md`](opensearch-provider.md) for mapping, migration and rollback details.

## Permission Model

Search-service applies workspace-level filtering in SQL. It then checks notebook candidates against
workspace-service internal permission API. Permission client failure is fail-closed with
`WORKSPACE_PERMISSION_UNAVAILABLE`; inaccessible candidate notes are not returned.

## Privacy

Search query text can contain sensitive information. Audit metadata records `qLength` and result
count only. It does not record plaintext `q`.

## Limitations

- no Kafka/RabbitMQ indexing
- no hard delete during mark-and-sweep reindex cleanup
- real cleanup production rollout still requires staged approval
- permission-aware exact total count for OpenSearch is future work
- no advanced highlighting
- no vector or semantic search
- no autocomplete
