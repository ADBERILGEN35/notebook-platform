# Audit Archive Object Storage (Faz 55)

## Scope

Faz 55 adds real upload support for `s3-compatible` archives from scheduled export scripts.
`gcs` and `azure` remain documented future providers.

## Provider model

- `local`: keep archive package on local output directory
- `s3-compatible`: upload data/checksum/manifest with AWS CLI (supports MinIO endpoints)
- `gcs` (future)
- `azure` (future)

## Archive key layout

```
<prefix>/source=<source>/year=<YYYY>/month=<MM>/day=<DD>/<file>
```

Example:

`audit-archive/source=identity/year=2026/month=05/day=08/audit-identity-...jsonl.gz`

## Upload order

1. data file
2. checksum sidecar
3. manifest (last, completion signal)

## S3/Object Lock notes

- Bucket must be pre-provisioned (script does not create bucket).
- Optional object lock envs:
  - `AUDIT_ARCHIVE_S3_OBJECT_LOCK_MODE=GOVERNANCE|COMPLIANCE`
  - `AUDIT_ARCHIVE_S3_OBJECT_LOCK_RETAIN_UNTIL_DATE`
  - `AUDIT_ARCHIVE_S3_OBJECT_LOCK_LEGAL_HOLD=ON|OFF`
- `COMPLIANCE` mode can be irreversible until retention expiration.

## IAM least privilege

Recommended permissions (prefix-scoped):

- `s3:PutObject`
- `s3:GetObject`
- `s3:HeadObject`
- `s3:ListBucket` (restricted prefix)
- optional for lock flows:
  - `s3:PutObjectRetention`
  - `s3:PutObjectLegalHold`

Avoid `s3:DeleteObject` for archive writer identity.

## MinIO/staging usage

- Set `AWS_ENDPOINT_URL`
- Use dedicated test bucket with object lock toggles validated separately
- Keep production and staging credentials isolated
