# Faz 115: SCIM Provider-specific Delta Sync POC

## Hedef

Okta, Microsoft Entra ID ve generic SCIM için provider capability / delta strategy / checkpoint / dry-run diagnostics foundation. Production scheduled sync, IdP mutation ve destructive deprovision yok.

## Scope

| Alan | İçerik |
|------|--------|
| Strategy model | `ScimDeltaProviderKind`, `ScimDeltaSyncStrategy`, `ScimDeltaStrategyResolver` |
| Diagnostics | `GET .../delta/readiness` (read-only) |
| Dry-run POC | `POST .../delta/dry-run` — sync run + checkpoint diagnostic only |
| Config | `SCIM_DELTA_PROVIDER_POC_ENABLED=false`, `SCIM_DELTA_DRY_RUN_ONLY=true` |
| UI | Enterprise security — Provider delta readiness card |
| Docs | Provider quirks, active=false semantics, warning codes |

## API

**Identity internal**

- `GET /internal/admin/scim/delta/readiness`
- `POST /internal/admin/scim/delta/dry-run`

**Gateway admin** (same auth as Faz 97)

- `GET /admin/identity/scim/delta/readiness`
- `POST /admin/identity/scim/delta/dry-run`

## Strategy matrix (POC)

| Provider | Selected strategy (typical) | Delta source label |
|----------|---------------------------|-------------------|
| okta | `LAST_MODIFIED_FILTER` when filtering | `lastModified-filter-poc:okta` |
| azure-ad / entra | `CURSOR_CHECKPOINT` when filtering | `cursor-checkpoint-poc:azure-ad` |
| generic | `DISABLED` or `FULL_SYNC_FALLBACK` | conservative |

## Non-goals

- Production scheduler / cron
- Remote IdP SCIM fetch loops
- User/group mutation from dry-run
- Hard delete / missing-from-delta deprovision

Detay: [`phase-115-summary.md`](phase-115-summary.md).
