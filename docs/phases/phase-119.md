# Faz 119: SCIM Delta Read-only Multi-page Cursor Loop POC

## Hedef

Faz 117–118 read-only remote fetch üzerine, manual dry-run sırasında provider pagination/cursor davranışını **bounded multi-page GET loop** ile gözlemlemek. Yalnızca aggregate diagnostic; production scheduler ve IdP mutation yok.

## Scope

| Bileşen | Açıklama |
|---------|----------|
| `ScimDeltaMultiPageRemoteFetcher` | Bounded page loop |
| `ScimDeltaPaginationContinuation` | Internal sanitized continuation (redacted toString) |
| `ScimDeltaStoppedReason` | Loop stop reason enum |
| Config | `SCIM_DELTA_REMOTE_MULTI_PAGE_*` |
| API/UI | `pagesObserved`, `stoppedReason`, limit flags |
| Evidence scripts | Multi-page fields |

## Bounds (defaults)

| Config | Default |
|--------|---------|
| `SCIM_DELTA_REMOTE_MULTI_PAGE_ENABLED` | `false` |
| `SCIM_DELTA_REMOTE_MAX_PAGES` | `1` |
| `SCIM_DELTA_REMOTE_MAX_RESOURCES` | `500` |
| `SCIM_DELTA_REMOTE_PAGE_DELAY_MS` | `0` |

## Non-goals

- Production scheduler
- Automatic retry worker
- Provider mutation / non-GET methods
- Raw cursor or next URL in API/logs

Detay: [`phase-119-summary.md`](phase-119-summary.md).
