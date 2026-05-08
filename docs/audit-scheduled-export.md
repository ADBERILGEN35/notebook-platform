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
- `AUTH_MODE` (`machine|bearer|cookie`)
- machine JWT inputs:
  - `MACHINE_JWT` or `MACHINE_JWT_PRIVATE_KEY_PATH`
  - `MACHINE_JWT_ISSUER`, `MACHINE_JWT_SUBJECT`, `MACHINE_JWT_AUDIENCE`
  - `MACHINE_JWT_SCOPE`, `MACHINE_JWT_TTL_SECONDS`, `MACHINE_JWT_KID` (optional)
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

## Auth model (Faz 54)

- Production path is `AUTH_MODE=machine` with short-lived JWT.
- Machine principal is scoped to export only (`admin:audit:export`), `token_type=machine`.
- User admin + MFA flow remains valid for manual export.
- CronJob remains disabled by default until secrets/key distribution are ready.

## Object storage upload (Faz 55)

- `AUDIT_ARCHIVE_UPLOAD_ENABLED=true` enables provider upload.
- Current implemented provider: `s3-compatible` (AWS CLI based).
- Upload order: data -> checksum -> manifest.
- Retry controls:
  - `AUDIT_ARCHIVE_UPLOAD_MAX_ATTEMPTS`
  - `AUDIT_ARCHIVE_UPLOAD_RETRY_SECONDS`
- Overwrite protection: `AUDIT_ARCHIVE_FAIL_IF_EXISTS=true`.

