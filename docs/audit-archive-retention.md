# Audit Archive Retention & WORM Foundation

## Scope

Faz 53 defines archive packaging and verification practices for exported audit files.
It does not provision cloud object storage or WORM buckets directly.

## Retention layers

- **Operational audit DB retention**: service-local policy (`docs/audit-retention.md`)
- **Archive retention**: scheduled export outputs stored in archive location

Recommended controls:

- minimum retention target per compliance profile
- explicit purge runbook with approval trail
- legal hold override path

## WORM strategy (provider-agnostic)

- AWS S3 Object Lock (governance/compliance mode)
- GCS retention policy / bucket lock
- Azure Immutable Blob Storage
- On-prem immutable NAS/WORM-capable storage

## Integrity & chain of custody

- SHA256 sidecar for every archive file
- manifest with schema version and generation metadata
- verification script before ingest/restore
- immutable write path in production archive target

## Encryption & access control

- encryption at rest in archive backend
- least-privilege read/write identities
- no audit archive secrets in Git
- access reviews and break-glass logging

## Restore verification

Run periodic drills:

1. pull file + manifest from archive
2. verify checksum and record count
3. parse JSONL sample and validate downstream readability
4. capture evidence in compliance runbook

