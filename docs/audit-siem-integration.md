# Audit SIEM Integration Foundation (Faz 48)

## Recommended feed format

Use JSONL export (`format=jsonl`) for SIEM ingestion:

- stable line-delimited JSON events
- source-tagged (`identity|workspace|content`)
- already redacted metadata at export layer

## Canonical fields

- `eventType`
- `actorUserId`
- `workspaceId`
- `requestId`
- `aggregateType`
- `aggregateId`
- `createdAt`
- `source`

## Operational ingestion pattern

1. Export by bounded date range via gateway admin endpoint.
2. Store exported file in secure operational workspace.
3. Push JSONL into SIEM collector/parser pipeline.
4. Alert on privileged event patterns and anomaly buckets.

## Provider-agnostic mapping notes

- Splunk: sourcetype `notebook:audit:jsonl`, extract `source,eventType,requestId`.
- Elastic: index template with keyword mappings for ids/eventType and date mapping for `createdAt`.
- Sentinel: custom log table with request correlation fields (`requestId`, `actorUserId`, `workspaceId`).

## Redaction and legal considerations

- Export payload intentionally masks sensitive metadata keys.
- Keep legal-hold/retention policy in ops runbooks; export endpoint does not enforce archival strategy.

## Future work (out of Faz 48 scope)

- Scheduled exports (Faz 53 foundation: ops-side script + CronJob template)
- Direct SIEM streaming/push connectors
- Object storage or WORM archival provisioning
- Near-real-time SIEM streaming (implemented as Faz 62 foundation)

## Faz 53 archive package additions

For scheduled ingestion, pair JSONL export with:

- manifest (`*.manifest.json`) including range/source/checksum metadata
- SHA256 sidecar (`*.sha256`) for integrity verification

Use `scripts/audit/verify-audit-archive.sh` before downstream SIEM import in high-assurance flows.

## Faz 62 streaming push addition

- Export/archive path forensic workflow olarak devam eder.
- Streaming push path detection/alerting icin near-real-time ayrik kanaldir.
- Faz 62 implementation `identity-service` outbox + worker pattern ile provider-agnostic olarak eklendi.
