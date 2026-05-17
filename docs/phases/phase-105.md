# Faz 105: Platform Retention Metrics + Grafana Dashboard

## Hedef

Faz 104'te eklenen `serviceSummaries` contract'ını production observability seviyesine taşımak için bounded-cardinality Micrometer metrics ve Grafana dashboard JSON eklemek.

Bu fazda destructive purge, scheduler, delete veya yeni retention count target implementation yapılmayacak. Amaç, platform retention readiness durumunu Prometheus/Grafana üzerinden izlenebilir hale getirmek ve operatörlerin content / notification / identity / audit-security durumunu tek bakışta takip etmesini sağlamaktır.

## Mevcut Durum

Faz 104'te platform retention plan response'una optional `serviceSummaries` alanı eklendi. Gateway merged target list'ten servis bazlı aggregate readiness üretir. Admin UI service readiness kartları gösterir. `targets[]` contract'ı backward-compatible kalmıştır.

Faz 104'te metrics kullanıcı onayıyla docs-only bırakılmıştır. `docs/platform-retention-dashboard-readiness.md` içinde önerilen metric ve panel yapısı vardır; ancak gateway Micrometer metric implementation ve Grafana dashboard JSON henüz yoktur.

## Scope İçi

- Api-gateway içinde platform retention service summary için bounded Micrometer metrics eklemek.
- `serviceSummaries` üretiminden sonra metrics publish etmek.
- Grafana dashboard JSON eklemek veya mevcut retention dashboard'una panel eklemek.
- Prometheus rule / alert önerilerini dokümante etmek; uygun ise rule dosyasına eklemek.
- Metrics cardinality testleri eklemek.
- Docs ve phase summary güncellemek.

## Scope Dışı

- Destructive purge.
- Retention scheduler.
- Content / notification / audit / identity delete.
- Yeni target count implementation.
- Workspace/search target dry-run count.
- eDiscovery export.
- Tenant-specific retention policy.
- Yeni microservice.
- Raw target/user/workspace/note id metric label'ları.
- Full production alert tuning.

## Metrics

Gateway/admin aggregation layer'da aşağıdaki bounded metrics eklenir.

### Required metrics

```text
platform_retention_service_summary_total{service,status}
platform_retention_service_targets_total{service,target_status}
platform_retention_service_warnings_total{service,warning_code}
```

### Optional metrics

```text
platform_retention_plan_generated_total{result}
platform_retention_service_blocked_targets_total{service}
platform_retention_service_capped_targets_total{service}
```

## Metric Label Rules

Allowed labels:

- `service`
  - `content-service`
  - `notification-service`
  - `identity-service`
  - `audit-security`
  - bounded fallback: `unknown`
- `status`
  - `READY`
  - `PARTIAL`
  - `DISABLED`
  - `UNAVAILABLE`
  - `BLOCKED_BY_HOLD`
  - `INVENTORY_ONLY`
  - `ERROR`
- `target_status`
  - `DRY_RUN_READY`
  - `INVENTORY_ONLY`
  - `PURGE_READY`
  - `DISABLED`
  - `ERROR`
  - `UNAVAILABLE`
- `warning_code`
  - known warning constants only
  - unknown warnings should map to `UNKNOWN_WARNING`

Forbidden labels:

- `userId`
- `email`
- `workspaceId`
- `noteId`
- `targetKey` if unbounded
- `requestId`
- legal hold key
- raw warning message

## Metrics Emission Rules

Metrics should be emitted after service summary calculation.

Rules:

- Do not emit raw target data.
- Do not emit per-user/per-workspace labels.
- If a plan generation fails before summary exists, emit only bounded failure metric.
- Metrics should not change response contract.
- Metrics should not make plan generation fail. Metrics emission failure must be swallowed/logged safely.

## Grafana Dashboard

Add dashboard JSON under:

- `observability/grafana/dashboards/platform-retention-readiness.json`

Dashboard panels:

- Retention service status by service
- Dry-run ready target count by service
- Inventory-only target count by service
- Legal-hold blocked target count by service
- Service unavailable warning count
- Capped count warning count
- Plan generation result count
- Last known readiness table

Dashboard style:

- Ops-focused
- No PII
- No user/workspace/note labels
- Links or annotations to runbooks if dashboard format supports it

## Prometheus Rules

If existing rules structure allows it, add:

- `observability/prometheus/rules/platform-retention-readiness.yml`

Suggested alerts:

- `PlatformRetentionServiceUnavailable`
  - Triggers when retention integration is expected but service summary status is `UNAVAILABLE`.
- `PlatformRetentionCappedCounts`
  - Triggers when capped warnings are observed repeatedly.
- `PlatformRetentionBlockedByLegalHoldHigh`
  - Warn only; legal hold may be intentional. Should be low severity unless product policy says otherwise.

If rules are too environment-specific, document examples only.

## Backend / Gateway

Likely files:

- `api-gateway/.../admin/retention/AdminPlatformRetentionProxyService.java`

New helper if needed:

- `PlatformRetentionMetricsPublisher`
- or equivalent package-private helper

Implementation notes:

- Keep service summary logic separate from metrics publishing if possible.
- Metrics publisher should receive already computed summaries and plan-level warnings.
- Use Micrometer `MeterRegistry`.
- Avoid dynamic label cardinality.

## Frontend

No UI feature required.

Frontend changes only if dashboard links or status copy need minor documentation alignment. Existing service readiness cards remain unchanged.

## Security / Privacy

- Metrics aggregate-only.
- No raw domain data.
- No note title/body.
- No comment body.
- No notification payload/body.
- No recipient/user email.
- No legal hold reason/key labels.
- No high-cardinality labels.
- No destructive action.

## Test Gereksinimleri

### Api-gateway

- Metrics emitted for `READY` service summary.
- Metrics emitted for `UNAVAILABLE` service summary.
- Target status metrics emitted with bounded labels.
- Warning metrics map known warning code correctly.
- Unknown warning maps to `UNKNOWN_WARNING`.
- Metrics publisher does not throw if summary is empty/null.
- Response contract remains unchanged.
- Existing platform retention tests pass.

### Observability

- Grafana dashboard JSON is valid JSON.
- Prometheus rule YAML is valid if added.
- Dashboard contains no forbidden label names.

### Docs

- `docs/platform-retention-dashboard-readiness.md` updated with implemented metrics.
- `docs/production-readiness.md` references dashboard/rules.
- Runbook links remain current.

## Kabul Kriterleri

- Api-gateway emits bounded platform retention readiness metrics.
- No high-cardinality or sensitive labels are used.
- Grafana dashboard JSON added.
- Prometheus rules added or documented.
- Existing `serviceSummaries` response remains backward-compatible.
- No destructive purge/action added.
- Gateway tests added and pass.
- Dashboard/rules validation performed where possible.
- Docs and phase summary updated.
