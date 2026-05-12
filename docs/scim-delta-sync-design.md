# SCIM Delta Sync Design (Faz 97)

Faz 97 delta sync icin foundation ekler; scheduled production delta sync, provider SDK entegrasyonu veya provider-specific remote mutation eklemez.

## Goals

- Large tenant full-sync maliyetini ve IdP rate-limit riskini azaltacak checkpoint modelini tasarlamak.
- Delta sync durumunu operator/admin tarafinda gorunur yapmak.
- Tombstone ve re-add semantiklerini sertlestirmek.
- Missing-from-delta sonucunu destructive action olarak yorumlamamak.

## Non-goals

- Production scheduler.
- Provider-specific cursor fetcher.
- IdP group mutation UI.
- Hard delete.
- Missing user/group sonucundan otomatik deprovision.
- SCIM bearer token veya raw payload gosterimi.

## Checkpoint model

`scim_sync_checkpoints`:

- `provider`
- `resource_type`: `USER` veya `GROUP`
- `sync_mode`: `FULL` veya `DELTA`
- `checkpoint_token`: nullable, secret kabul edilmez ama response'ta sadece `checkpointPresent` doner.
- `last_successful_sync_at`
- `last_attempt_at`
- `status`: `IDLE`, `RUNNING`, `FAILED`, `STALE`

`scim_sync_runs`:

- `provider`, `resource_type`, `sync_mode`
- `status`: `STARTED`, `COMPLETED`, `FAILED`, `CANCELLED`
- processed/created/updated/deprovisioned/skipped/error counters
- `last_error_code`, `last_error_summary`, `request_id`

## Delta sources

Order of preference for a future phase:

1. Provider-side cursor/checkpoint if the provider exposes a stable cursor.
2. `lastModified` or equivalent filter if filtering is supported and tested.
3. Manual or scheduled full sync fallback when neither cursor nor filter is safe.

## Safety rules

- Delta sync must never hard-delete users.
- Missing from a delta response does not imply deletion.
- Deprovision only explicit `active=false` or explicit DELETE/deprovision event.
- Re-add with same `externalId` may reactivate a deprovisioned SCIM user and records `SCIM_USER_REACTIVATED`.
- Same email with a different `externalId` is a conflict/manual-review case and records `SCIM_EXTERNAL_ID_CONFLICT_DETECTED`.
- `externalId` is the preferred identity key. Email is secondary and risky for rehire/name-change cases.
- Duplicate `externalId` is rejected.

## Tombstone / re-add fields

User tombstone fields:

- `deprovisioned_at`
- `deprovision_reason`
- `last_scim_external_id`
- `reactivated_at`

`active=false` and SCIM DELETE keep the identity link and revoke refresh tokens. Re-add with the same `externalId` reactivates the existing user rather than creating a second identity.

## Metrics

Identity service metrics:

- `scim_sync_runs_total{provider,resourceType,mode,result}`
- `scim_sync_processed_total{resourceType,result}` (reserved)
- `scim_sync_errors_total{errorCode}` (reserved)
- `scim_checkpoint_age_seconds{resourceType}` (reserved)
- `scim_external_id_conflicts_total` (reserved)

Do not add userId or email labels.

## Future production requirements

- Provider certification tests with Okta, Entra ID, Google Workspace, OneLogin, and Generic SCIM 2.0.
- Retry/backoff and Retry-After handling.
- Cursor expiration handling.
- Stale checkpoint recovery.
- Explicit admin-visible dry-run/report before enabling writes.
- Alerting on high conflict/error counts.
