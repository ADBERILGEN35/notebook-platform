# SCIM delta remote fetch — sandbox enable overlays (Faz 118)

Example value fragments for **manual dry-run** remote GET POC only. **Do not commit real bearer tokens or production IdP URLs with live credentials.**

## Prerequisites

1. `SCIM_DELTA_PROVIDER_POC_ENABLED=true` on identity-service.
2. Kubernetes Secret (or ExternalSecret) contains key `scim-delta-remote-bearer-token` (or custom key from overlay).
3. `scimDeltaRemoteFetch.bearerTokenFromSecret.enabled=true` so Helm injects `SCIM_DELTA_REMOTE_BEARER_TOKEN` via `secretKeyRef`.
4. Admin gateway access for `POST /admin/identity/scim/delta/dry-run`.

## Apply

```bash
helm upgrade notebook-platform ./deploy/helm/notebook-platform \
  -f deploy/helm/notebook-platform/examples/scim-delta-remote-fetch/okta.overlay.example.yaml
```

Replace `config.scimDeltaRemoteBaseUrl` with your sandbox SCIM base URL (no token in Git).

## Evidence

See `scripts/scim/scim-delta-evidence-formats.md` and `scripts/scim/generate-scim-delta-evidence-template.sh`.
