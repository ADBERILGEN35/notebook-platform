# SCIM Provider Compatibility Matrix (Faz 97)

Faz 97 provider-specific production delta sync rollout yapmaz. Bu matrix, enterprise IdP davranislarini izlemek ve ilerideki delta sync kararlarini guvenli yapmak icin kullanilir.

## Current defaults

| Config | Default |
|--------|---------|
| `SCIM_PROVIDER_TYPE` | `generic` |
| `SCIM_DELTA_SYNC_ENABLED` | `false` |
| `SCIM_DELTA_SYNC_MODE` | `disabled` |
| `SCIM_PROVIDER_SUPPORTS_BULK` | `false` |
| `SCIM_PROVIDER_SUPPORTS_FILTERING` | `true` |
| `SCIM_PROVIDER_SUPPORTS_PATCH` | `true` |
| `SCIM_PROVIDER_SUPPORTS_NESTED_GROUPS` | `false` |
| `SCIM_PROVIDER_RATE_LIMIT_AWARE` | `true` |
| `SCIM_PROVIDER_MAX_PAGE_SIZE` | `100` |
| `SCIM_DELTA_PROVIDER_POC_ENABLED` | `false` |
| `SCIM_DELTA_DRY_RUN_ONLY` | `true` |
| `SCIM_DELTA_REMOTE_FETCH_ENABLED` | `false` |
| `SCIM_DELTA_REMOTE_BASE_URL` | empty |
| `SCIM_DELTA_REMOTE_MAX_PAGE_SIZE` | `100` |

## Compatibility matrix

| Provider | Users | Groups | Bulk | Pagination / filtering | Deprovision | Known quirks |
|----------|-------|--------|------|-------------------------|-------------|--------------|
| Okta | Create, PUT/PATCH update, active status changes are common. | Group Push can create/update pushed groups and membership. | Treat as unsupported unless tenant integration validates it. | Filtering support depends on integration profile; validate userName/externalId filters. | Okta commonly sends `active=false` for deprovision and may send group DELETE when pushed group is unlinked with delete. | Group Push is source-of-truth oriented; target-side membership edits can cause sync issues. Okta notes active status has provider-specific lifecycle behavior. |
| Microsoft Entra ID / Azure AD | SCIM provisioning service supports create/update/delete profile. Microsoft SCIM API docs also describe Users GET/POST/PATCH/DELETE. | Groups GET/POST/PATCH/DELETE exist in Microsoft SCIM API docs; provisioning-service behavior must be tested per app. | Microsoft SCIM API service provider config reports bulk unsupported. | Cursor pagination and filtering exist, but filters are constrained; Microsoft docs list strict field/operator support and page-size limits. | Missing from a page/delta is not deletion. Deprovision only explicit `active=false` or DELETE. | Whitespace and compound filters can be rejected; nested group membership is not evaluated by some member filters. |
| Google Workspace | Autoprovisioning syncs active, suspended, or deleted users to supported third-party apps. | App-specific; group scope can affect which users are provisioned. | Treat as unsupported until the specific app profile proves otherwise. | App-profile-specific; do not assume full SCIM filter semantics. | Google docs state autoprovisioning does not include archived users. | Google Workspace has many app-specific SCIM profiles; validate exact payloads before enabling diagnostics beyond generic. |
| OneLogin | Expected SCIM flow includes get user by userName filter, create, get by id, update, get users, delete. | Expected flow includes get groups, create group, patch group. | Treat as unsupported unless tested. | Provider flow expects filters; rate limits apply to OneLogin APIs. | OneLogin docs describe suspend with `active:false`; delete may be hard or soft by target choice, but deleted resources should not be returned. | OneLogin documents account-level API rate limits; do not build a tight loop without retry/backoff. |
| Generic SCIM 2.0 | Support only the local SCIM endpoints documented in `docs/scim-provisioning.md`. | USER and optional nested GROUP memberships are local capabilities. | Optional local Bulk MVP behind `SCIM_BULK_ENABLED`. | Count/startIndex style local list endpoints; provider delta is unknown. | `active=false` and DELETE are soft deprovision locally. | Provider-specific quirks unknown; keep `SCIM_DELTA_SYNC_ENABLED=false`. |

Sources reviewed:

- Okta SCIM concepts: `https://developer.okta.com/docs/concepts/scim/`
- Okta SCIM 2.0 protocol reference: `https://developer.okta.com/docs/api/openapi/okta-scim/guides/scim-20/`
- Microsoft Entra SCIM API reference: `https://learn.microsoft.com/en-us/entra/identity/app-provisioning/entra-id-scim-api-reference`
- Microsoft Entra provisioning guide: `https://learn.microsoft.com/en-us/entra/identity/app-provisioning/use-scim-to-provision-users-and-groups`
- Google Workspace Admin autoprovisioning examples: `https://support.google.com/a/answer/9073631`
- OneLogin SCIM implementation guide: `https://developers.onelogin.com/scim/implement-scim-api`
- OneLogin rate limit docs: `https://developers.onelogin.com/api-docs/2/oauth20-tokens/get-rate-limit`

## Compatibility warnings

The diagnostics status reports warning codes only, never tokens or raw SCIM payloads:

- `SCIM_DISABLED`
- `SCIM_TOKEN_MISSING`
- `DELTA_ENABLED_WITH_DISABLED_MODE`
- `DELTA_LAST_MODIFIED_FILTER_UNAVAILABLE`
- `RATE_LIMIT_AWARENESS_DISABLED`
- `PROVIDER_NESTED_GROUPS_BUT_LOCAL_DISABLED`

## Faz 115 delta POC strategies

| Provider | POC strategy when `SCIM_DELTA_PROVIDER_POC_ENABLED=true` | Deprovision semantics |
|----------|----------------------------------------------------------|------------------------|
| Okta | `LAST_MODIFIED_FILTER` if filtering enabled; else `FULL_SYNC_FALLBACK` | `active=false` or explicit DELETE only; missing from delta page does not deprovision |
| Entra / azure-ad | `CURSOR_CHECKPOINT` if filtering enabled; else fallback | Missing from pagination/delta is not deletion; `active=false` or DELETE only |
| generic | `DISABLED` unless `SCIM_DELTA_SYNC_ENABLED` + filtering; else `FULL_SYNC_FALLBACK` | Missing-from-delta never deprovisions locally |

### Known quirks (operations)

- **Okta:** Group Push is source-of-truth oriented; validate filter profiles per app; rate limits apply to management APIs.
- **Entra:** Strict filter grammar; whitespace/compound filters may be rejected; bulk unsupported in service provider config.
- **Generic:** Unknown IdP behavior — keep POC disabled until tenant validation.

## Faz 118 sandbox remote fetch wiring

| Provider | Example overlay | Secret key (default) | Notes |
|----------|-----------------|----------------------|--------|
| Okta | `examples/scim-delta-remote-fetch/okta.overlay.example.yaml` | `scim-delta-remote-bearer-token` | lastModified filter diagnostic GET |
| Entra | `entra.overlay.example.yaml` | same | `$top` / cursor diagnostic GET |
| Generic | `generic.overlay.example.yaml` | same | count/startIndex fallback GET |

Bearer token via Kubernetes `secretKeyRef` / ExternalSecret only — never in Git values. Sandbox evidence: `scripts/scim/scim-delta-remote-fetch-smoke.sh`.

## Rollout rule

Provider-specific delta sync requires explicit future approval, tenant-level validation, rate-limit testing, and a decision on provider cursor versus `lastModified` filter behavior. Faz 115–118 add POC diagnostics, optional read-only single-page GET on manual dry-run, and secret wiring examples — **no production scheduler**.
