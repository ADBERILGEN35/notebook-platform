# workspace-service

Workspace, notebook, tag ve invitation domainlerini yoneten servis.

## Calistirma

Bagimliliklari baslat:

```bash
docker compose -f docker-compose.dev.yml up -d
```

Servisi lokalde calistir:

```bash
export DB_URL=jdbc:postgresql://localhost:5432/notebook_platform
export DB_USER=notebook
export DB_PASSWORD=notebook
export IDENTITY_SERVICE_URL=http://localhost:8081
./gradlew :workspace-service:bootRun
```

Full compose:

```bash
docker compose up --build
```

## Env Degiskenleri

- `SERVER_PORT`: default `8082`
- `DB_URL` veya `DB_HOST`, `DB_PORT`, `DB_NAME`
- `DB_USER`, `DB_PASSWORD`
- `DB_RUNTIME_USER`, `DB_RUNTIME_PASSWORD`: runtime datasource credential; bos ise `DB_USER`/`DB_PASSWORD`
- `DB_MIGRATION_USER`, `DB_MIGRATION_PASSWORD`: Flyway credential; bos ise `DB_USER`/`DB_PASSWORD`
- `IDENTITY_SERVICE_URL`: Spring HTTP Interface client base URL
- `INVITATION_TTL_DAYS`: default `7`
- `INVITATION_ACCEPT_BASE_URL`: accept link base URL
- `INVITATION_EXPOSE_TOKEN_IN_RESPONSE`: dev icin `true`; production'da `false` olmalidir
- `INTERNAL_API_TOKEN`: local/dev legacy fallback; production'da reddedilir
- `INTERNAL_API_TOKEN_PRIMARY`: rotation-ready primary internal token
- `INTERNAL_API_TOKEN_SECONDARY`: optional secondary internal token during rotation
- `INTERNAL_AUTH_MODE`: `static-token`, `service-jwt` veya `dual`
- `TRUSTED_SERVICE_CONTENT_SERVICE_PUBLIC_KEY_PATH`: content-service service JWT public key path
- `TRUSTED_SERVICE_CONTENT_SERVICE_KID`: trusted content-service key id
- `APP_RLS_ENABLED`: transaction icinde PostgreSQL tenant setting uygular
- `APP_RLS_STRICT_WORKSPACE_HEADER`: aggregate-id endpointlerde `X-Workspace-Id` zorunlu kilar

Flyway `workspace_flyway_schema_history` tablosunu kullanir. Bu, ayni PostgreSQL database icinde identity-service migration history ile cakismayi onler.

## Role Modeli

Workspace rolleri:

- `OWNER`: workspace update/delete, owner/member yonetimi, invitation create/revoke, notebook/tag tam yetki.
- `ADMIN`: invitation create, member list, workspace update, notebook/tag tam yetki; owner yonetimi yapamaz.
- `MEMBER`: workspace read, notebook read; notebook ozel yetkisi varsa notebook islemleri.

Notebook rolleri:

- `OWNER`: notebook tam yetki
- `EDITOR`: notebook update
- `COMMENTER`: Faz 4 content yorumlari icin tutulur
- `VIEWER`: read-only

Her workspace'te birden fazla `OWNER` olabilir. Son owner silinemez ve son owner'in rolu dusurulemez.

Internal permission contract MVP karari: workspace `MEMBER` olup notebook icin ozel `notebook_members` kaydi olmayan kullanici notebook'u `VIEWER` gibi okuyabilir (`canRead=true`, diger yetkiler `false`). Workspace `OWNER` ve `ADMIN` notebook uzerinde tam yetkilidir.

## Invitation Flow

Invitation token plaintext sadece olusturma aninda uretilir. DB'de `token_hash` saklanir. Faz 3'te email gercek gonderilmez; accept link log'a yazilir. Dev modda response `inviteToken` ve `acceptUrl` donebilir, production'da `INVITATION_EXPOSE_TOKEN_IN_RESPONSE=false` kullanilmalidir.

Accept sirasinda:

- token hashlenir
- invitation pending olmali
- `expiresAt` gecmemis olmali
- `acceptedAt` ve `revokedAt` bos olmali
- `X-User-Email` invitation email'i ile eslesmeli
- `X-User-Id` workspace member olarak eklenir

## Tenant Enforcement

Aktif enforcement uygulama seviyesindedir: workspaceId iceren servis methodlari `X-User-Id` ile membership/role kontrolu yapar.

Faz 11 itibariyla tenant-aware akislarda servis transaction'i icinde `TenantDatabaseSession.applyWorkspace(workspaceId)` cagrilir. `APP_RLS_ENABLED=true` ise bu cagri `SET LOCAL app.current_workspace_id = '<workspace-id>'` uygular. Request sonunda `TenantCleanupFilter` ThreadLocal tenant context'i temizler.

Faz 12 itibariyla runtime ve migration DB credential ayrimi hazirdir. `scripts/db/workspace-create-roles.sql` non-owner runtime role icin template saglar. `scripts/db/enable-force-rls-workspace.sql` opt-in FORCE RLS scriptidir; `workspaces` tablosu user-level list/create akislari nedeniyle kapsam disinda tutulur.

Not: Tam DB-level blocking icin non-owner runtime role, `APP_RLS_ENABLED=true`, gerekirse `APP_RLS_STRICT_WORKSPACE_HEADER=true` ve opt-in FORCE RLS rollout'u birlikte test edilmelidir. Default `APP_RLS_ENABLED=false` kalir.

## Endpointler

- `POST /workspaces`
- `GET /workspaces`
- `GET /workspaces/{workspaceId}`
- `PATCH /workspaces/{workspaceId}`
- `DELETE /workspaces/{workspaceId}`
- `GET /workspaces/{workspaceId}/members`
- `PATCH /workspaces/{workspaceId}/members/{userId}/role`
- `DELETE /workspaces/{workspaceId}/members/{userId}`
- `POST /workspaces/{workspaceId}/notebooks`
- `GET /workspaces/{workspaceId}/notebooks`
- `GET /notebooks/{notebookId}`
- `PATCH /notebooks/{notebookId}`
- `DELETE /notebooks/{notebookId}`
- `GET /notebooks/{notebookId}/members`
- `PUT /notebooks/{notebookId}/members/{userId}`
- `PATCH /notebooks/{notebookId}/members/{userId}/role`
- `DELETE /notebooks/{notebookId}/members/{userId}`
- `POST /workspaces/{workspaceId}/tags`
- `GET /workspaces/{workspaceId}/tags`
- `PATCH /tags/{tagId}`
- `DELETE /tags/{tagId}`
- `PUT /notebooks/{notebookId}/tags/{tagId}`
- `DELETE /notebooks/{notebookId}/tags/{tagId}`
- `POST /workspaces/{workspaceId}/invitations`
- `GET /workspaces/{workspaceId}/invitations`
- `POST /invitations/accept`
- `POST /invitations/{invitationId}/revoke`

Internal endpointler gateway route'una eklenmez; yalnizca servisler arasi network icindir:

- `GET /internal/notebooks/{notebookId}/permissions?userId={userId}`
- `GET /internal/workspaces/{workspaceId}/tags/{tagId}/exists?scope=NOTE`

Internal auth mode:

- `static-token`: `X-Internal-Token` zorunlu.
- `service-jwt`: `X-Service-Authorization: Bearer <service-jwt>` zorunlu.
- `dual`: service JWT varsa dogrulanir, yoksa static token fallback denenir.

Service JWT validation `iss=content-service`, `aud=workspace-service`, `token_type=service`, `kid`,
`exp` ve endpoint scope kontrolu yapar. Legacy `INTERNAL_API_TOKEN` prod profilinde kabul edilmez.

## Curl Ornekleri

Workspace create:

```bash
curl -s http://localhost:8082/workspaces \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 11111111-1111-1111-1111-111111111111" \
  -H "X-User-Email: owner@example.com" \
  -d '{"name":"Product Team","type":"TEAM"}'
```

Workspace list:

```bash
curl -s http://localhost:8082/workspaces \
  -H "X-User-Id: 11111111-1111-1111-1111-111111111111"
```

Member role update:

```bash
curl -s -X PATCH http://localhost:8082/workspaces/<workspaceId>/members/<userId>/role \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 11111111-1111-1111-1111-111111111111" \
  -d '{"role":"ADMIN"}'
```

Notebook create:

```bash
curl -s http://localhost:8082/workspaces/<workspaceId>/notebooks \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 11111111-1111-1111-1111-111111111111" \
  -d '{"name":"Roadmap","icon":"book"}'
```

Tag create:

```bash
curl -s http://localhost:8082/workspaces/<workspaceId>/tags \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 11111111-1111-1111-1111-111111111111" \
  -d '{"name":"Planning","color":"#22cc88","scope":"NOTEBOOK"}'
```

Invitation create:

```bash
curl -s http://localhost:8082/workspaces/<workspaceId>/invitations \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 11111111-1111-1111-1111-111111111111" \
  -H "X-User-Email: owner@example.com" \
  -d '{"email":"invitee@example.com","role":"MEMBER"}'
```

Invitation accept:

```bash
curl -s http://localhost:8082/invitations/accept \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 22222222-2222-2222-2222-222222222222" \
  -H "X-User-Email: invitee@example.com" \
  -d '{"token":"<inviteToken>"}'
```

Internal permission contract:

```bash
curl -s 'http://localhost:8082/internal/notebooks/<notebookId>/permissions?userId=11111111-1111-1111-1111-111111111111' \
  -H "X-Service-Authorization: Bearer <service-jwt>"
```

Internal tag exists:

```bash
curl -s 'http://localhost:8082/internal/workspaces/<workspaceId>/tags/<tagId>/exists?scope=NOTE' \
  -H "X-Service-Authorization: Bearer <service-jwt>"
```

## Test

```bash
./gradlew :workspace-service:test
./gradlew :workspace-service:check
```

## Pagination

List endpointleri Faz 19 itibariyla raw array yerine `PageResponse<T>` doner:

```json
{
  "items": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0,
  "hasNext": false,
  "hasPrevious": false
}
```

Standart query parametreleri: `page`, `size`, `sort`. Default `page=0`, `size=20`, max
`size=100`.

Sort allow-list:

- workspaces: `name`, `createdAt`, `updatedAt`
- workspace members: `createdAt`, `updatedAt`, `joinedAt`, `role`
- notebooks: `name`, `createdAt`, `updatedAt`
- notebook members: `createdAt`, `updatedAt`, `role`
- tags: `name`, `createdAt`
- invitations: `createdAt`, `expiresAt`, `email`

## Observability + Hardening

- Console logs JSON formatindadir.
- `X-Request-Id` response ve error body icinde doner; yoksa servis UUID uretir.
- Validation hatalari `fieldErrors` listesi doner.
- `/actuator/health`, `/actuator/metrics`, `/actuator/prometheus` hazirdir.
- Identity lookup HTTP Interface Client icin circuit breaker, timeout ve GET retry hazirdir; lookup opsiyonel oldugu icin hata durumunda invitation create fail-open devam eder.
- Internal permission/tag endpointleri `INTERNAL_AUTH_MODE` ile korunur; service JWT modunda eksik header `401 INTERNAL_AUTH_REQUIRED`, yetersiz scope `403 INSUFFICIENT_SERVICE_SCOPE` doner.
- `SPRING_PROFILES_ACTIVE=prod` ve `INTERNAL_AUTH_MODE=service-jwt` ile trusted content-service public key zorunludur.
- `APP_RLS_ENABLED=true` tenant-scoped transactionlarda PostgreSQL `app.current_workspace_id` ayarini yapar.
- Kritik workspace/notebook/invitation aksiyonlari `workspace_audit_events` tablosuna yazilir.
