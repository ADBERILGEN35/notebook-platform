# Audit Scheduled Export (Faz 53)

## Decision

Scheduled export runs as an **ops-side CronJob/script** that calls existing gateway endpoint
`GET /admin/audit-events/export`.  
Gateway does not host a scheduler.

## Why this model

- Reuses Faz 48 export contract and redaction.
- Keeps gateway business scope small.
- Fits Kubernetes CronJob and ops-runbook workflows.

## Inputs

- `BASE_URL`
- `SOURCE_LIST` (default `identity,workspace,content`)
- `FORMAT` (`jsonl` preferred)
- `LOOKBACK_HOURS` or explicit `CREATED_FROM` + `CREATED_TO`
- `AUTH_MODE` (`bearer|cookie`)
- `BEARER_TOKEN` or `ADMIN_COOKIE` (from Secret, not hardcoded)
- archive options (`OUTPUT_DIR`, `COMPRESS`, `WRITE_MANIFEST`)

## Output package

Per source/window:

- `audit-{source}-{from}-{to}.jsonl` (or `.jsonl.gz`)
- `audit-{source}-{from}-{to}.manifest.json`
- `audit-{source}-{from}-{to}.jsonl[.gz].sha256`

Manifest fields:

- `exportId`, `source`, `format`, `createdFrom`, `createdTo`
- `recordCount`, `generatedAt`, `generatedBy`
- `checksumSha256`, `fileName`, `schemaVersion`
- optional operational fields: `authMode`, `requestId`, `contentType`

## Auth caveat

Faz 52 MFA enforcement protects admin export endpoint.  
For production scheduling, use dedicated machine identity/service principal design (future phase).
This phase keeps CronJob default disabled and documents required secret placeholders.

