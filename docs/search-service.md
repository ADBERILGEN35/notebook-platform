# Search Service

Faz 25 introduces `search-service` as the provider-agnostic search foundation. The initial provider
is PostgreSQL full-text search with `simple` text configuration. OpenSearch or Elasticsearch remain
future providers.

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

## PostgreSQL FTS

`search_documents.search_vector` is a generated `tsvector`:

- title: weight `A`
- tags and notebook name: weight `B`
- content text: weight `C`

The index uses PostgreSQL `simple` config. Turkish and English stemming are future work because
portable PostgreSQL language configuration differs by installation.

## Permission Model

Search-service applies workspace-level filtering in SQL. It then checks notebook candidates against
workspace-service internal permission API. Permission client failure is fail-closed with
`WORKSPACE_PERMISSION_UNAVAILABLE`; inaccessible candidate notes are not returned.

## Privacy

Search query text can contain sensitive information. Audit metadata records `qLength` and result
count only. It does not record plaintext `q`.

## Limitations

- no OpenSearch/Elasticsearch provider
- no Kafka/RabbitMQ indexing
- no hard delete during mark-and-sweep reindex cleanup
- no advanced highlighting
- no vector or semantic search
- no autocomplete
