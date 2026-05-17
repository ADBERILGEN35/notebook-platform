# SCIM Delta Sandbox Evidence (Faz 120)

Sanitized evidence for **manual dry-run** remote fetch POC validation in Okta, Microsoft Entra ID, or generic SCIM sandboxes. This does **not** authorize production scheduled delta sync.

## Evidence artifact

| File | Purpose |
|------|---------|
| `scim-delta-remote-fetch-evidence.json` | Machine-readable sanitized dry-run summary |
| `scim-delta-certification-checklist.md` | Human checklist (from template generator) |
| Helm/ExternalSecret attestation | Secret wiring only (no token values) |

Generate checklist template:

```bash
bash scripts/scim/generate-scim-delta-certification-evidence-template.sh okta
bash scripts/scim/generate-scim-delta-certification-evidence-template.sh entra
bash scripts/scim/generate-scim-delta-certification-evidence-template.sh generic
```

Collect live JSON (requires gateway admin token + sandbox IdP secret):

```bash
export GATEWAY_BASE_URL=https://api.staging.example.com
export ADMIN_ACCESS_TOKEN=<from-secret-manager>
export SCIM_DELTA_EVIDENCE_OUTPUT_DIR=./evidence/cr-1234
bash scripts/scim/scim-delta-remote-fetch-smoke.sh
bash scripts/scim/validate-scim-delta-evidence.sh "$SCIM_DELTA_EVIDENCE_OUTPUT_DIR/scim-delta-remote-fetch-evidence.json"
```

## JSON schema (sanitized)

| Field | Type | Required | Notes |
|-------|------|----------|--------|
| `evidenceSchemaVersion` | string | yes | `scim-delta-evidence-v1` |
| `generatedAt` | ISO-8601 UTC | yes | |
| `evidenceStatus` | string | yes | `passed` \| `skipped` \| `failed` \| `privacy_violation` |
| `skipReason` | string? | if skipped | |
| `providerType` | string | yes | `okta` \| `azure-ad` \| `generic` |
| `selectedStrategy` | string | yes | Symbolic strategy name |
| `remoteFetchEnabled` | boolean | yes | |
| `remoteFetchConfigured` | boolean | yes | No token value |
| `remoteFetchAttempted` | boolean | yes | |
| `remoteMultiPageEnabled` | boolean | yes | |
| `dryRunOnly` | boolean | yes | Must be `true` for certification |
| `pagesObserved` | number | yes | Bounded page count |
| `fetchedResourceCount` | number | yes | Aggregate only |
| `nextCursorPresent` | boolean | yes | Hint only — no cursor value |
| `stoppedReason` | string | yes | See Faz 119 enum |
| `pageLimitReached` | boolean | yes | |
| `resourceLimitReached` | boolean | yes | |
| `providerErrorClass` | string | yes | Faz 116 classifier |
| `retryAfterSeconds` | number? | no | Bounded seconds |
| `nextRecommendedAttemptAt` | string? | no | ISO instant |
| `warnings` | string[] | yes | Symbolic codes only |
| `certificationHints` | object | yes | Derived PASS/FAIL/REVIEW hints (Faz 120) |

### Forbidden in evidence (blockers)

- Raw SCIM list response / `Resources` payloads
- Bearer token, API key, `Authorization` header
- Raw `nextCursor`, `skiptoken`, `@odata.nextLink` URL values
- `userName`, email, `displayName`, or other IdP PII
- `deprovisionedCount` > 0 from dry-run (must remain 0 in platform)

## Certification hints object

Populated by smoke script (symbolic only):

| Hint key | Meaning |
|----------|---------|
| `privacyChecksPassed` | No forbidden patterns in captured JSON |
| `dryRunOnlyConfirmed` | `dryRunOnly=true` |
| `onePageGetAttempted` | `remoteFetchAttempted` and `pagesObserved>=1` when enabled |
| `paginationObservedOrValidStop` | Multi-page cursor seen OR `NO_NEXT_CURSOR` / `SINGLE_PAGE_ONLY` |
| `rateLimitDiagnosticReady` | Classifier fields present when 429 tested |
| `missingFromDeltaNoDeprovision` | Documented platform policy (always true for POC) |

Hints inform [`scim-delta-provider-certification.md`](scim-delta-provider-certification.md); final **certified / blocked / needs review** is human sign-off.

## Change-request bundle

1. Sanitized `scim-delta-remote-fetch-evidence.json` (validated)
2. Completed provider checklist markdown
3. Config attestation: remote fetch + multi-page flags, max bounds, secretKeyRef (no literals)
4. Link to CI fixture run (`ci-scim-delta-remote-fetch-fixtures`) for the release SHA

## Staging gate + CR handoff (Faz 121)

GitHub Actions workflow: `.github/workflows/scim-delta-readiness.yml`

| Job | When | Secrets |
|-----|------|---------|
| `scim-delta-fixtures` | PR + `main` push | None |
| `scim-delta-sandbox-evidence` | `staging` push or `workflow_dispatch` | Optional sandbox secrets |

### Repository secrets / variables

| Name | Required | Description |
|------|----------|-------------|
| `SCIM_DELTA_SANDBOX_GATEWAY_BASE_URL` | For live run | Staging gateway base URL |
| `SCIM_DELTA_SANDBOX_ADMIN_ACCESS_TOKEN` | For live run | Admin JWT (never logged) |
| `SCIM_DELTA_SANDBOX_PROVIDER` | Optional | Set in workflow; dispatch input overrides |
| `SCIM_DELTA_SANDBOX_EXPECT_READY` | Optional repo variable | `true` → exit `2` if not **certified** |

Orchestrator:

```bash
bash scripts/scim/run-scim-delta-sandbox-evidence.sh
```

### Workflow artifacts

- `scim-delta-sandbox-evidence.json`
- `scim-delta-certification-checklist.md`
- `scim-delta-sandbox-summary.md`

### Certification result (workflow)

| Result | Meaning |
|--------|---------|
| `certified` | Required checks passed |
| `needs-review` | Non-blocking gaps |
| `blocked` | Security/protocol blocker |
| `skipped` | Secrets/environment missing |

Exit codes: `0` ok/skip/review, `2` readiness gap, `3` privacy, `4` schema, `5` blocked.

## Related docs

- [`scim-delta-provider-certification.md`](scim-delta-provider-certification.md)
- [`scim-sync-diagnostics.md`](scim-sync-diagnostics.md)
- [`scripts/scim/scim-delta-evidence-formats.md`](../scripts/scim/scim-delta-evidence-formats.md)
