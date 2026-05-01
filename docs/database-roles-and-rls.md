# Database Roles and RLS

Faz 12 hedefi, Faz 11'de eklenen `TenantContext` + transaction-scoped
`set_config('app.current_workspace_id', ..., true)` altyapisini production'a daha yakin bir
PostgreSQL role modeliyle tamamlamaktir.

## Role Model

Production deployment iki credential kullanmalidir:

| Role | Amac | Yetki |
| --- | --- | --- |
| Migration owner | Flyway migration calistirir ve tablo sahibi olabilir. | DDL, migration history, schema changes |
| Runtime app role | Servis runtime datasource'u bu kullanici ile baglanir. | Gerekli tablolarda `SELECT`, `INSERT`, `UPDATE`, kontrollu `DELETE`; DDL yok |

Runtime role superuser, table owner veya `BYPASSRLS` olmamalidir. Bu model RLS'in tablo owner
bypass riskini azaltir. Migration owner runtime'da kullanilmamalidir.

## Configuration

workspace-service ve content-service runtime datasource'u su env degerlerini destekler:

| Env | Amac |
| --- | --- |
| `DB_RUNTIME_URL` | Runtime datasource URL. Bos ise `DB_URL` kullanilir. |
| `DB_RUNTIME_USER` | Runtime app user. Bos ise `DB_USER` kullanilir. |
| `DB_RUNTIME_PASSWORD` | Runtime app password. Bos ise `DB_PASSWORD` kullanilir. |
| `SPRING_FLYWAY_USER` / `SPRING_FLYWAY_PASSWORD` | Standard Spring Boot Flyway credential override. |
| `DB_MIGRATION_USER` / `DB_MIGRATION_PASSWORD` | Compose/template inputs that are mapped to `SPRING_FLYWAY_USER/PASSWORD`. |

Flyway credential ayrimi application.yml icinde defaultlanmaz. Env verilmezse Flyway datasource
credential'ini kullanir; bu Testcontainers ve local dev akisini basit tutar. Flyway URL ayrimi
gerekiyorsa Spring Boot'un standart `SPRING_FLYWAY_URL` env'i kullanilabilir.
Local/dev Docker Compose varsayilan olarak tek `notebook` kullanicisini kullanir; role ayrimi prod
hazirligi olarak dokumante edilmis ve SQL scriptleriyle orneklenmistir.

## Setup Scripts

Role setup scriptleri:

- `scripts/db/workspace-create-roles.sql`
- `scripts/db/content-create-roles.sql`
- `scripts/rls/check-db-role-permissions.sql`
- `scripts/rls/check-rls-status.sql`
- `scripts/rls/check-current-workspace-setting.sql`
- `scripts/rls/run-preflight-checks.sh`
- `scripts/rls/run-force-rls-enable.sh`
- `scripts/rls/run-force-rls-disable.sh`

Bu scriptler production'da DBA/infra tarafindan, gercek parolalar secret manager'dan verilerek
calistirilmalidir. Scriptlerdeki placeholder parolalar commit edilmemelidir.
Preflight scriptlerinin kullanim sirasi [`runtime-rls-rollout.md`](runtime-rls-rollout.md) ve
`scripts/rls/README.md` icinde tanimlidir.

Faz 22 adds a staging-like automated validation task:

```bash
./gradlew rlsIntegrationTest
```

It creates Testcontainers PostgreSQL databases, validates non-owner runtime roles, confirms runtime
roles cannot run DDL, applies FORCE RLS opt-in scripts, confirms cross-tenant reads return no rows
and runs the disable scripts to prove rollback metadata changes.

## FORCE RLS

Mevcut migrationlar tenant-scoped tablolarda RLS'i enable eder. Faz 12'de `FORCE ROW LEVEL SECURITY`
kalici migration'a eklenmedi; opt-in script olarak birakildi:

- `scripts/db/enable-force-rls-workspace.sql`
- `scripts/db/enable-force-rls-content.sql`
- `scripts/db/disable-force-rls-workspace.sql`
- `scripts/db/disable-force-rls-content.sql`

Workspace tarafinda `workspaces` tablosu FORCE RLS kapsamina alinmadi. Bu tablo `workspace_id`
tasimaz ve `GET /workspaces`, `POST /workspaces` gibi user-level akislara hizmet eder.

FORCE RLS oncesi kosullar:

- Runtime role table owner degil.
- `APP_RLS_ENABLED=true` ile tenant-aware servis akislari test edildi.
- Aggregate endpointlerde strict workspace header rollout plani net.
- Integration testler non-owner runtime role ile RLS izolasyonunu dogruluyor.
- `scripts/rls/check-db-role-permissions.sql` runtime role'un table owner, superuser veya
  `BYPASSRLS` olmadigini dogruluyor.
- `scripts/rls/check-rls-status.sql` hedef tablolarin RLS/policy durumunu gosteriyor.

Testcontainers ortaminda ana database kullanicisi superuser olabildigi icin owner-bypass engelleme
davranisi superuser uzerinden garanti gibi raporlanmaz. Testler non-owner runtime role'un RLS'e
takildigini ve FORCE RLS opt-in komutlarinin tablo metadata'sinda aktif/pasif hale geldigini
dogrular; production garantisi superuser olmayan migration owner + non-owner runtime role ile
tamamlanir.

The staging-like tests intentionally do not connect to a real staging or production database.
Deployed staging validation should use `docs/rls-staging-validation-report-template.md`.

## Strict Workspace Header

`APP_RLS_STRICT_WORKSPACE_HEADER=false` default'tur. `true` oldugunda aggregate-id ile baslayan
tenant-scoped endpointlerde `X-Workspace-Id` zorunlu olur:

- workspace-service: `/notebooks/{notebookId}`, notebook member endpointleri, `/tags/{tagId}`,
  notebook-tag attach/detach, `/invitations/{invitationId}/revoke`
- content-service: `/notebooks/{notebookId}/notes`, `/notes/{noteId}`, note version/link/comment/tag
  aggregate endpointleri, `/comments/{commentId}`

Header eksikse `400 MISSING_WORKSPACE_CONTEXT`, resolved workspace ile celisirse
`400 INVALID_WORKSPACE_CONTEXT` doner. Path/query ile workspaceId request basinda net olan
endpointlerde header zorunlu degildir.

## Rollback

RLS rollout sorununda sirali geri donus:

1. `APP_RLS_STRICT_WORKSPACE_HEADER=false` yap. Aggregate endpointler header olmadan eski
   application-level authorization davranisina doner.
2. `APP_RLS_ENABLED=false` yap. Servis transaction'lari DB session setting set etmeyi birakir;
   application-level checks aktif kalir.
3. FORCE RLS acildiysa ilgili disable scriptini migration owner ile calistir:
   - `scripts/db/disable-force-rls-workspace.sql`
   - `scripts/db/disable-force-rls-content.sql`
4. Runtime role grant problemi varsa migration owner credential ile acil mudahale yap; runtime'da
   owner credential'i kalici kullanma.

Rollback tetikleyicileri:

- Tenant-scoped endpointlerde beklenmeyen 0 result/403/500 artis.
- Flyway runtime user karisikligi nedeniyle startup failure.
- Connection pool'un runtime credential ile DML yapamamasi.
- Strict header rollout'u tamamlanmamis client'larda yaygin 400 hatalari.

Ayrintili staged rollout ve rollback tablosu:
[`runtime-rls-rollout.md`](runtime-rls-rollout.md).
