# Faz 110: Dedicated Retention Datasource Helm Template + Ops Handoff

## Hedef

Production'da cross-workspace aggregate retention count path'leri için güvenli, opt-in, secret'sız GitOps/Helm şablonu ve DBA/platform operasyon handoff'u. Runtime davranışı değişmez (future binding).

## Scope içi

- `retentionDatasource.*` Helm values (default `enabled: false`)
- Optional Deployment env wiring (`*_RETENTION_DATASOURCE_*`)
- `docs/retention-datasource-ops-handoff.md`
- `examples/retention-datasource/README.md`
- GitOps commented placeholders; runbook + production-readiness güncellemeleri

## Scope dışı

- Flyway / SQL migration, BYPASSRLS role creation in repo
- Backend DataSource binding
- Prod default enable, secret hardcode, destructive purge

## Future runtime binding

Pod env is wired when `enabled: true`; Java services do **not** read these variables in Faz 110. Dry-run still uses normal DB until a later faz implements retention `DataSource`.

Detay: [`phase-110-summary.md`](phase-110-summary.md).
