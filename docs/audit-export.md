# Audit Export (Faz 48)

## Endpoint

Gateway admin export endpoint:

- `GET /admin/audit-events/export`

Required query params:

- `source=identity|workspace|content`
- `format=csv|jsonl`
- `createdFrom` (ISO-8601)
- `createdTo` (ISO-8601)

Optional filters:

- `eventType`, `actorUserId`, `workspaceId`, `aggregateType`, `aggregateId`, `requestId`, `sort`

## Security model

- Browser calls gateway with user session/bearer auth.
- Gateway enforces platform admin authorization.
- Gateway calls internal audit APIs with service JWT server-side.
- Service JWT is never exposed to frontend.

## Limits

- Max range days: default `31`
- Max records: default `10000`
- Internal proxy page size: default `200`

Errors:

- `AUDIT_EXPORT_RANGE_REQUIRED`
- `AUDIT_EXPORT_RANGE_TOO_LARGE`
- `AUDIT_EXPORT_TOO_LARGE`
- `INVALID_AUDIT_EXPORT_FORMAT`
- `AUDIT_EXPORT_DISABLED`

## Formats

### CSV

Columns:

- `id,source,eventType,actorUserId,workspaceId,aggregateType,aggregateId,requestId,ipAddress,userAgent,metadataJson,createdAt`

CSV injection defense:

- values starting with `=`, `+`, `-`, `@` are prefixed with `'`.

### JSONL

- `application/x-ndjson`
- one JSON event per line
- preferred format for SIEM ingestion.

## Redaction

Gateway applies export-time redaction on metadata keys containing:

- `password`, `token`, `secret`, `key`, `authorization`, `cookie`, `private`, `credential`, `session`, `jwt`

## Observability

Gateway emits structured logs:

- export completed/failed
- admin user id/email
- source, format, range
- exported count
- requestId
- durationMs

## Scheduled export foundation (Faz 53)

- Scheduled execution is **ops-side** (`scripts/audit/scheduled-export-audit-events.sh`) and
  optionally rendered as Helm CronJob (`templates/cronjob-audit-export.yaml`).
- Gateway keeps export logic; no gateway scheduler is added.
- Recommended scheduled format: `jsonl` (`csv` remains available for manual exports).
- Archive package includes:
  - data file (`.jsonl` or `.jsonl.gz`)
  - manifest (`.manifest.json`)
  - checksum sidecar (`.sha256`)

## Machine identity (Faz 54)

- Export endpoint supports machine principal with `token_type=machine`.
- Required claims/policy: issuer allowlist, audience `api-gateway`, scope `admin:audit:export`,
  max token TTL.
- Machine auth is scoped to export endpoint and is intended for scheduled jobs only.

## Object storage upload (Faz 55)

- Scheduled export script supports `local` and `s3-compatible` archive providers.
- Upload flow writes data/checksum first and manifest last.
- Optional S3 object lock headers can be enabled for governance/compliance retention workflows.
