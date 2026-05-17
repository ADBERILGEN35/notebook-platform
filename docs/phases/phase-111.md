# Faz 111: Backend Retention DataSource Binding

## Hedef

Content, notification, workspace ve search servislerinde retention dry-run count repository'lerinin, `*_RETENTION_DATASOURCE_ENABLED=true` iken dedicated Hikari pool kullanması; default'ta mevcut runtime datasource.

## Scope

- `*RetentionDataSourceProperties` + `*RetentionJdbcTemplateConfig` (4 servis)
- `*RetentionCountRepository` dedicated `JdbcTemplate` injection
- `application.yml` env binding (Helm Faz 110 ile uyumlu)
- Unit/config tests; docs güncellemesi

## Davranış

| `enabled` | Config | Sonuç |
|-----------|--------|--------|
| `false` | — | Primary `DataSource` / mevcut davranış |
| `true` | complete | Dedicated pool + retention counts |
| `true` | eksik URL/user/password | Startup fail-fast (`IllegalStateException`, secret loglanmaz) |

Detay: [`phase-111-summary.md`](phase-111-summary.md).
