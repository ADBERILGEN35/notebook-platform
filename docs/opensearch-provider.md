# OpenSearch Provider for Search Service

Phase 31 adds a provider abstraction to `search-service` while keeping PostgreSQL FTS as the default and canonical local index state.

## Provider Modes

Configuration:

- `SEARCH_PROVIDER=postgres|opensearch`
- `SEARCH_DUAL_WRITE_ENABLED=false`
- `SEARCH_FALLBACK_TO_POSTGRES=false`

Default mode is `postgres`. PostgreSQL remains the fallback-capable local index because `search_documents` already stores source version, reindex markers, orphan cleanup state, and FTS fields.

`opensearch` mode sends search queries to OpenSearch and projects index writes to OpenSearch after PostgreSQL canonical state accepts the write. `SEARCH_DUAL_WRITE_ENABLED=true` can be used during migration to populate OpenSearch while queries still use the selected provider. Query fallback defaults to `false` because silently mixing PostgreSQL and OpenSearch result sets can hide consistency gaps. Staging can set fallback to `true` for availability during rollout; production steady state should turn it off after validation.

## OpenSearch Config

Required when `SEARCH_PROVIDER=opensearch`:

- `OPENSEARCH_URL`

Optional:

- `OPENSEARCH_USERNAME`
- `OPENSEARCH_PASSWORD`
- `OPENSEARCH_INDEX_NOTES=notebook-notes`
- `OPENSEARCH_CONNECT_TIMEOUT_MS=1000`
- `OPENSEARCH_SOCKET_TIMEOUT_MS=3000`
- `OPENSEARCH_TLS_ENABLED=false`
- `OPENSEARCH_TRUSTSTORE_PATH`

Credentials must come from Kubernetes Secret or External Secrets. Do not put credentials in `application.yml`, Helm ConfigMaps, GitOps values, logs, or runbooks.

## Index Mapping

Create the index with:

```bash
OPENSEARCH_URL=https://opensearch.example.internal:9200 \
OPENSEARCH_USERNAME="$OPENSEARCH_USERNAME" \
OPENSEARCH_PASSWORD="$OPENSEARCH_PASSWORD" \
scripts/search/opensearch-create-index.sh
```

The Phase 31 mapping uses the standard analyzer and these fields:

- `noteId`, `workspaceId`, `notebookId`, `createdBy`, `updatedBy`: `keyword`
- `title`: `text` with `keyword` subfield
- `contentText`, `tagsText`, `notebookName`: `text`
- `noteCreatedAt`, `noteUpdatedAt`, `archivedAt`, `indexedAt`: `date`
- `sourceVersion`: `integer`

Turkish/English analyzers, edge ngram autocomplete, suggestions, and semantic/vector search are future work.

## Indexing Behavior

Document id is `noteId`.

Upsert flow:

1. Search-service extracts `contentText` and `tagsText`.
2. PostgreSQL `search_documents` applies the existing `sourceVersion` skip rule.
3. If the write is current, OpenSearch receives the remote projection when selected provider is `opensearch` or dual-write is enabled.

Archive flow:

1. PostgreSQL canonical row is archived.
2. OpenSearch projection receives a partial update that sets `archivedAt`.
3. Hard delete is not used.

OpenSearch does not perform its own optimistic source-version script in Phase 31. PostgreSQL remains the authority for idempotency and source-version ordering.

## Search Query

OpenSearch query shape:

- required `workspaceId` term filter
- optional `notebookId` term filter
- `archivedAt` missing filter
- `multi_match` over `title^3`, `tagsText^2`, `notebookName^2`, `contentText`
- `_score` then `noteUpdatedAt` sort

Highlighting is not enabled in Phase 31. The response snippet is currently simple title-based text, matching the existing minimal behavior.

## Permission Filtering

Workspace-service permission filtering remains mandatory and fail-closed. Search-service asks the provider for oversampled candidates, currently requested size multiplied by 3 and capped at 100, then filters results through the permission client.

Known limitation: `totalElements` and `last` describe the filtered candidate window, not an exact provider-wide authorized total. More exact pagination needs permission snapshot indexing or cursor-based post-filter pagination.

## Reindex And Cleanup

Reindex still writes through `SearchIndexService`, so PostgreSQL state and OpenSearch projection are both updated according to provider mode and dual-write settings.

Mark-and-sweep remains PostgreSQL canonical. Cleanup archives PostgreSQL orphan rows and projects archived candidates to OpenSearch when OpenSearch writes are enabled. Large cleanup batches should be validated in staging before increasing batch sizes because Phase 31 does not add a remote bulk cleanup API.

## Deployment

Helm values:

```yaml
search:
  provider: postgres
  dualWriteEnabled: "false"
  fallbackToPostgres: "false"
  opensearch:
    url: ""
    usernameSecretKey: opensearch-username
    passwordSecretKey: opensearch-password
    indexNotes: notebook-notes
```

GitOps staging can use:

```yaml
search:
  provider: opensearch
  dualWriteEnabled: "true"
  fallbackToPostgres: "true"
```

Production steady state should use:

```yaml
search:
  provider: opensearch
  dualWriteEnabled: "false"
  fallbackToPostgres: "false"
```

No OpenSearch Helm dependency is added. The chart assumes an external or managed OpenSearch endpoint. If cluster egress is restricted, allow egress from `search-service` to the external endpoint and port in the environment-specific NetworkPolicy layer.

## Health And Metrics

Actuator contributor:

- `searchProviderHealth`

Metrics:

- `search_provider_requests_total{provider,operation}`
- `search_provider_failures_total{provider,operation}`
- `search_provider_latency{provider,operation}`
- `search_provider_fallback_total{operation}`
- `opensearch_index_upserts_total`
- `opensearch_search_queries_total`

Alert on OpenSearch failures, fallback spikes, index upsert failures, and search p95 latency growth.

## Migration Plan

1. Keep `SEARCH_PROVIDER=postgres`.
2. Create OpenSearch index with `scripts/search/opensearch-create-index.sh`.
3. Enable dual-write in staging and run reindex/backfill.
4. Switch staging query provider to OpenSearch with fallback enabled.
5. Validate permissions, pagination behavior, reindex, archive, and cleanup.
6. Disable fallback.
7. Repeat for production with an explicit rollback window.

Rollback:

1. Set `SEARCH_PROVIDER=postgres`.
2. Set `SEARCH_DUAL_WRITE_ENABLED=false`.
3. Keep OpenSearch index intact for investigation unless explicit deletion is approved.

Deletion script is intentionally an example and requires `CONFIRM_DELETE_INDEX=true`.

## Faz 34 Permission Projection Fields

OpenSearch `_source` projection now includes:

- `visibilityMode` (keyword)
- `permissionVersion` (integer)
- `workspaceReadable` (boolean)
- `restricted` (boolean)
- `permissionIndexedAt` (date)

Query shape remains `workspaceId + archivedAt + q` in MVP. Permission filtering stays in
application layer, where unrestricted candidates are fast-pathed and restricted candidates require
runtime permission verification.
