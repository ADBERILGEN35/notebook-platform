# content-service

Note, note version, note link, comment, note tag ve basit note search domainlerini yoneten servis.

## Calistirma

```bash
docker compose -f docker-compose.dev.yml up -d

export DB_URL=jdbc:postgresql://localhost:5432/notebook_platform
export DB_USER=notebook
export DB_PASSWORD=notebook
export WORKSPACE_SERVICE_URL=http://localhost:8082
./gradlew :content-service:bootRun
```

Full compose:

```bash
docker compose up --build
```

## Env Degiskenleri

- `SERVER_PORT`: default `8083`
- `DB_URL` veya `DB_HOST`, `DB_PORT`, `DB_NAME`
- `DB_USER`, `DB_PASSWORD`
- `DB_RUNTIME_USER`, `DB_RUNTIME_PASSWORD`: runtime datasource credential; bos ise `DB_USER`/`DB_PASSWORD`
- `DB_MIGRATION_USER`, `DB_MIGRATION_PASSWORD`: Flyway credential; bos ise `DB_USER`/`DB_PASSWORD`
- `WORKSPACE_SERVICE_URL`: workspace permission client base URL
- `WORKSPACE_INTERNAL_API_TOKEN`: local/dev legacy fallback; production'da reddedilir
- `WORKSPACE_INTERNAL_API_TOKEN_PRIMARY`: rotation-ready active internal token
- `WORKSPACE_INTERNAL_API_TOKEN_SECONDARY`: optional rotation bookkeeping value
- `ALLOW_UNKNOWN_BLOCK_TYPES`: default `false`
- `APP_RLS_ENABLED`: transaction icinde PostgreSQL tenant setting uygular
- `APP_RLS_STRICT_WORKSPACE_HEADER`: aggregate-id endpointlerde `X-Workspace-Id` zorunlu kilar

Flyway `content_flyway_schema_history` tablosunu kullanir. Bu, ayni PostgreSQL database icinde diger servis migration history tablolarindan ayridir.

## Versioning

`notes` tablosu current mutable state tutar. Her create/update/restore isleminde `note_versions` tablosuna immutable snapshot eklenir. `version_number` note bazinda monoton artar; eski version kayitlari mutate edilmez.

## Block JSON

`content_blocks` JSONB saklanir ve root array olmak zorundadir. Her block:

- `id` zorunlu
- `type` zorunlu
- `content` varsa array
- `props` varsa object
- `children` varsa recursive array

Desteklenen type listesi: `paragraph`, `heading_1`, `heading_2`, `heading_3`, `bullet_list_item`, `numbered_list_item`, `code`, `mermaid`, `todo`, `callout`, `quote`, `divider`, `image`, `table`.

## Link Parsing

Create/update/restore sirasinda senkron parse edilir. Desteklenen formatlar:

- `props.noteId`
- `props.targetNoteId`
- `props.href = note://{uuid}`
- content item icinde `href = note://{uuid}`

Outgoing linkler replace strategy ile guncellenir. Self-link ignore edilir. Target note ayni workspace icinde yoksa `INVALID_NOTE_LINK` doner.

## Permission Dependency

Content-service Spring HTTP Interface Client ile workspace-service contract'ina baglidir:

- `GET /internal/notebooks/{notebookId}/permissions?userId={userId}`
- `GET /internal/workspaces/{workspaceId}/tags/{tagId}/exists?scope=NOTE`

Faz 6 itibariyla bu endpointler workspace-service icinde gercek implementedir. `WORKSPACE_INTERNAL_API_TOKEN_PRIMARY` set edilmisse content-service tum internal GET cagrilarinda `X-Internal-Token` header'i gonderir. Local/dev icin legacy `WORKSPACE_INTERNAL_API_TOKEN` fallback olarak kalir; production'da kabul edilmez. Permission client fail-closed calisir: workspace-service 5xx/connection/circuit breaker hatalarinda `503 WORKSPACE_SERVICE_UNAVAILABLE`, permission false ise `403 NOTEBOOK_ACCESS_DENIED`, tag exists false ise `404 TAG_NOT_FOUND` doner.

## Endpointler

- `POST /notebooks/{notebookId}/notes`
- `GET /notes/{noteId}`
- `GET /notebooks/{notebookId}/notes`
- `PATCH /notes/{noteId}`
- `DELETE /notes/{noteId}`
- `GET /notes/{noteId}/versions`
- `GET /notes/{noteId}/versions/{versionNumber}`
- `POST /notes/{noteId}/restore/{versionNumber}`
- `GET /notes/{noteId}/links/outgoing`
- `GET /notes/{noteId}/links/incoming`
- `GET /notes/{noteId}/backlinks`
- `POST /notes/{noteId}/comments`
- `GET /notes/{noteId}/comments`
- `PATCH /comments/{commentId}`
- `DELETE /comments/{commentId}`
- `POST /comments/{commentId}/resolve`
- `POST /comments/{commentId}/reopen`
- `PUT /notes/{noteId}/tags/{tagId}`
- `DELETE /notes/{noteId}/tags/{tagId}`
- `GET /notes/{noteId}/tags`
- `GET /notes/search?workspaceId={workspaceId}&q={query}`

## Curl Ornekleri

Note create:

```bash
curl -s http://localhost:8083/notebooks/<notebookId>/notes \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 11111111-1111-1111-1111-111111111111" \
  -H "X-Workspace-Id: 22222222-2222-2222-2222-222222222222" \
  -d '{"title":"Roadmap","contentBlocks":[{"id":"b1","type":"paragraph","content":[]}]}'
```

Note update:

```bash
curl -s -X PATCH http://localhost:8083/notes/<noteId> \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 11111111-1111-1111-1111-111111111111" \
  -d '{"title":"Roadmap v2","contentBlocks":[{"id":"b1","type":"paragraph","props":{"href":"note://<targetNoteId>"}}]}'
```

Versions:

```bash
curl -s http://localhost:8083/notes/<noteId>/versions \
  -H "X-User-Id: 11111111-1111-1111-1111-111111111111"
```

Restore:

```bash
curl -s -X POST http://localhost:8083/notes/<noteId>/restore/1 \
  -H "X-User-Id: 11111111-1111-1111-1111-111111111111"
```

Comment create:

```bash
curl -s http://localhost:8083/notes/<noteId>/comments \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 11111111-1111-1111-1111-111111111111" \
  -d '{"blockId":"b1","content":"Looks good"}'
```

Tag attach:

```bash
curl -s -X PUT http://localhost:8083/notes/<noteId>/tags/<tagId> \
  -H "X-User-Id: 11111111-1111-1111-1111-111111111111"
```

Search:

```bash
curl -s 'http://localhost:8083/notes/search?workspaceId=<workspaceId>&q=roadmap' \
  -H "X-User-Id: 11111111-1111-1111-1111-111111111111"
```

## Test

```bash
./gradlew :content-service:test
./gradlew :content-service:check
```

## Observability + Hardening

- Console logs JSON formatindadir.
- `X-Request-Id` response ve error body icinde doner; yoksa servis UUID uretir.
- Validation hatalari `fieldErrors` listesi doner.
- `/actuator/health`, `/actuator/metrics`, `/actuator/prometheus` hazirdir.
- Workspace permission client circuit breaker, timeout ve GET retry ile sarilidir.
- Permission client unavailable ise fail-closed `503 WORKSPACE_SERVICE_UNAVAILABLE` doner.
- Internal token `WORKSPACE_INTERNAL_API_TOKEN_PRIMARY` ile gonderilir; production'da workspace-service `INTERNAL_API_TOKEN_PRIMARY` ile ayni aktif secret kullanilmalidir.
- `SPRING_PROFILES_ACTIVE=prod` ile `WORKSPACE_INTERNAL_API_TOKEN_PRIMARY` bos ise servis fail-fast eder.
- Faz 11 tenant-aware akislarda `TenantDatabaseSession.applyWorkspace(workspaceId)` kullanir. `APP_RLS_ENABLED=true` ise transaction icinde `SET LOCAL app.current_workspace_id = '<workspace-id>'` uygulanir; request sonunda ThreadLocal context temizlenir.
- Faz 12 runtime ve migration DB credential ayrimini destekler. `scripts/db/content-create-roles.sql` non-owner runtime role icin template saglar. `scripts/db/enable-force-rls-content.sql` opt-in FORCE RLS scriptidir.
- Tam DB-level blocking icin non-owner runtime role, `APP_RLS_ENABLED=true`, gerekirse `APP_RLS_STRICT_WORKSPACE_HEADER=true` ve opt-in FORCE RLS rollout'u birlikte test edilmelidir; default `APP_RLS_ENABLED=false` kalir.
- Kritik note/comment/tag aksiyonlari `content_audit_events` tablosuna yazilir.
- Block JSON validation max depth ve max size ile sinirlandirilir:
  - `CONTENT_BLOCKS_MAX_DEPTH`
  - `CONTENT_BLOCKS_MAX_JSON_BYTES`
