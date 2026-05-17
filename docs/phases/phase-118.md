# Faz 118: SCIM Delta Remote Fetch Secret Wiring + Sandbox Evidence

## Hedef

Faz 117 read-only remote fetch kodu hazırken, bearer token ve base URL’nin Git’e yazılmadan K8s üzerinden nasıl verileceğini, sandbox Okta/Entra/generic testinin nasıl kanıtlanacağını ve change request artifact’larını netleştirmek.

## Scope

| Bileşen | Açıklama |
|---------|----------|
| Helm `scimDeltaRemoteFetch.bearerTokenFromSecret` | `SCIM_DELTA_REMOTE_BEARER_TOKEN` via `secretKeyRef` |
| ConfigMap | Secret name/key refs only (no token value) |
| Example overlays | Okta, Entra, generic + GitOps staging/dev examples |
| ExternalSecret fragment | Staging example YAML (placeholder paths) |
| Scripts | Evidence template, smoke (sanitized), CI fixtures |
| Docs | scim-sync-diagnostics, delta design, provider matrix, production-readiness |

## Non-goals

- Production scheduler
- Multi-page cursor loop
- Provider mutation or non-GET methods
- Real credentials in Git
- Live sandbox execution in CI (fixtures only)

Detay: [`phase-118-summary.md`](phase-118-summary.md).
