# SCIM Delta Provider Certification Checklist (Faz 120)

Use this checklist **before** any production scheduled delta sync proposal. Certification covers **read-only manual dry-run** remote fetch POC (Faz 117–119) plus documented provisioning semantics — not live scheduler rollout.

## Result codes

| Result | Meaning |
|--------|---------|
| **certified** | All required criteria met for this provider in sandbox |
| **blocked** | Blocker observed — do not proceed |
| **needs review** | Non-blocking gap — architect/owner decision |

## Blockers (any = blocked)

- Token, `Authorization` header, or raw SCIM body in evidence, logs, or API responses
- POST/PATCH/PUT/DELETE used during delta remote fetch POC
- Missing-from-delta caused deprovision (`deprovisionedCount>0` on dry-run)
- Unbounded pagination (pages/resources beyond configured max)
- Raw cursor or full next URL in evidence, API, or logs
- Provider auth failure messages exposing token material
- PII in metric labels or structured logs

## Non-blocking / needs review

- No next cursor because sandbox dataset fits one page (`NO_NEXT_CURSOR`)
- Retry-After not reproduced in sandbox
- Rate limit (429) not reproducible
- Generic provider lacks filtering — `FULL_SYNC_FALLBACK` only
- Multi-page intentionally disabled (`SINGLE_PAGE_ONLY`)
- Timeout/bad-response tested via simulation only (document in checklist)

---

## Common acceptance criteria (all providers)

| # | Criterion | Evidence | Blocker if fail |
|---|-----------|----------|-----------------|
| C1 | Auth / config | `remoteFetchConfigured=true`, secretKeyRef attestation | Auth failure with token leak |
| C2 | One-page GET | `remoteFetchAttempted=true`, `pagesObserved>=1`, `providerErrorClass=NONE` or documented error class | Non-GET or raw body leak |
| C3 | Pagination | Multi-page: `pagesObserved>1` OR valid `stoppedReason` (`NO_NEXT_CURSOR`, `SINGLE_PAGE_ONLY`, limits) | Unbounded loop |
| C4 | Rate limit | 429 test **or** documented N/A in sandbox | Retry loop without stop |
| C5 | Retry-After | Bounded `retryAfterSeconds` when 429 tested **or** documented N/A | Raw Retry-After header in API |
| C6 | Timeout / bad JSON | Simulated or live `TIMEOUT` / `BAD_RESPONSE` diagnostic **or** documented N/A | Crash / raw body leak |
| C7 | Missing-from-delta | Dry-run `deprovisionedCount=0`; policy text in warnings | Any deprovision |
| C8 | `active=false` | Documented: soft deprovision only (see `scim-provisioning.md`) | N/A (doc) |
| C9 | DELETE | Documented: explicit DELETE/deprovision event only | N/A (doc) |
| C10 | `externalId` | Documented: preferred key; conflict policy | N/A (doc) |
| C11 | Privacy | `validate-scim-delta-evidence.sh` PASS; no forbidden fields | Any blocker above |

---

## Okta

| Field | Value |
|-------|--------|
| Provider name | Okta |
| Sandbox tenant/environment | |
| Test date | |
| Tested by | |
| App version / Git SHA | |
| Strategy selected | `LAST_MODIFIED_FILTER` (expected when filtering on) |
| Remote fetch enabled | yes / no |
| Multi-page enabled | yes / no |
| Max pages / max resources | |

| Check | Result (pass/fail/N/A) | Notes |
|-------|------------------------|-------|
| C1 Auth success | | SCIM app + bearer via secret |
| C2 One-page GET | | `meta.lastModified` filter diagnostic GET |
| C3 Multi-page or valid stop | | Group Push quirks — document |
| C4 Rate-limit (429) | | |
| C5 Retry-After | | |
| C6 Timeout / bad response | | |
| C7 Missing-from-delta | | `SCIM_DELTA_MISSING_USER_IGNORED` |
| C8 active=false semantics | | |
| C9 DELETE semantics | | Group Push unlink behavior |
| C10 externalId uniqueness | | |
| C11 Privacy checks | | |

**Result:** certified / blocked / needs review

---

## Microsoft Entra ID / Azure AD

| Field | Value |
|-------|--------|
| Provider name | Microsoft Entra ID (`azure-ad`) |
| Sandbox tenant/environment | |
| Test date | |
| Tested by | |
| App version / Git SHA | |
| Strategy selected | `CURSOR_CHECKPOINT` (expected) |
| Remote fetch enabled | yes / no |
| Multi-page enabled | yes / no |
| Max pages / max resources | |

| Check | Result | Notes |
|-------|--------|-------|
| C1 Auth success | | |
| C2 One-page GET | | `$top` / skiptoken diagnostic |
| C3 Multi-page or valid stop | | `@odata.nextLink` sanitized |
| C4 Rate-limit (429) | | |
| C5 Retry-After | | |
| C6 Timeout / bad response | | Strict filter grammar |
| C7 Missing-from-delta | | Missing page ≠ delete |
| C8 active=false semantics | | |
| C9 DELETE semantics | | |
| C10 externalId uniqueness | | |
| C11 Privacy checks | | |

**Result:** certified / blocked / needs review

---

## Generic SCIM 2.0

| Field | Value |
|-------|--------|
| Provider name | Generic |
| Sandbox tenant/environment | |
| Test date | |
| Tested by | |
| App version / Git SHA | |
| Strategy selected | `FULL_SYNC_FALLBACK` / `DISABLED` |
| Remote fetch enabled | yes / no |
| Multi-page enabled | yes / no |
| Max pages / max resources | |

| Check | Result | Notes |
|-------|--------|-------|
| C1 Auth success | | |
| C2 One-page GET | | `count`/`startIndex` fallback |
| C3 Multi-page or valid stop | | Often `startIndex` pagination |
| C4 Rate-limit (429) | | |
| C5 Retry-After | | |
| C6 Timeout / bad response | | |
| C7 Missing-from-delta | | |
| C8 active=false semantics | | |
| C9 DELETE semantics | | |
| C10 externalId uniqueness | | |
| C11 Privacy checks | | |

**Result:** certified / blocked / needs review

---

## Staging evidence gate (Faz 121)

After checklist completion in sandbox, attach GitHub Actions artifacts from `scim-delta-sandbox-evidence` job:

1. Download workflow artifacts (`scim-delta-sandbox-evidence-*.zip`)
2. Confirm `certificationResult` in JSON is **certified** or document **needs-review** approval
3. Paste `scim-delta-sandbox-summary.md` into the change request
4. Sign the completed checklist markdown

Manual dispatch: Actions → **SCIM Delta Readiness** → Run workflow → choose provider (`okta` / `entra` / `generic`).

## Production scheduler gate

Do **not** enable production scheduled delta sync until:

1. Target provider checklist is **certified** (not merely needs review) for the tenant profile.
2. Architecture review approves cursor vs `lastModified` strategy per [`scim-delta-sync-design.md`](scim-delta-sync-design.md).
3. Rate-limit runbook and max bounds signed off.
4. `SCIM_DELTA_SYNC_ENABLED` / scheduler work tracked in a **separate approved phase** (out of Faz 120 scope).
