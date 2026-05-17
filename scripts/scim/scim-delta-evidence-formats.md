# SCIM delta remote fetch — evidence formats (Faz 118)

Sanitized evidence for change requests and sandbox validation. **Never** attach raw SCIM list responses, bearer tokens, `Authorization` headers, or IdP PII.

## Manual dry-run evidence (JSON)

File: `scim-delta-remote-fetch-evidence.json`

| Field | Type | Notes |
|-------|------|--------|
| `generatedAt` | ISO-8601 UTC | Script timestamp |
| `evidenceStatus` | string | `passed` \| `skipped` \| `failed` \| `privacy_violation` |
| `skipReason` | string? | e.g. `missing_gateway_or_admin_token`, `remote_fetch_disabled` |
| `providerType` | string | From readiness/dry-run API |
| `selectedStrategy` | string | e.g. `LAST_MODIFIED_FILTER`, `CURSOR_CHECKPOINT` |
| `remoteFetchEnabled` | boolean | Config flag |
| `remoteFetchConfigured` | boolean | URL + token material present (no token value) |
| `remoteFetchAttempted` | boolean | GET was attempted |
| `dryRunOnly` | boolean | Must remain `true` for POC |
| `fetchedResourceCount` | number | Aggregate only |
| `pageObserved` | number | Pages observed (POC: single page) |
| `nextCursorPresent` | boolean | Cursor/pagination hint only |
| `providerErrorClass` | string | Faz 116 classifier |
| `retryAfterSeconds` | number? | Bounded seconds only |
| `nextRecommendedAttemptAt` | string? | ISO instant |
| `warnings` | string[] | Symbolic codes only |

### Forbidden in evidence

- Raw SCIM response body
- Bearer token / API key
- `Authorization` header value
- `userName`, email, displayName, or other PII fields from IdP

## Change-request bundle

1. `scim-delta-remote-fetch-evidence.json` (from smoke script or manual copy from admin UI fields)
2. Helm render snippet showing `secretKeyRef` for `SCIM_DELTA_REMOTE_BEARER_TOKEN` (redact secret name if policy requires)
3. ExternalSecret sync status (PASS/FAIL, no key material)
4. `kubectl` env check: confirm `SCIM_DELTA_REMOTE_FETCH_ENABLED` and **absence** of token in ConfigMap

Generate blank template:

```bash
bash scripts/scim/generate-scim-delta-evidence-template.sh
```
