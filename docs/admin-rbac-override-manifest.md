# admin-rbac-overrides.yaml manifest

Path (GitOps / cluster): `deploy/gitops/environments/{env}/admin-rbac-overrides.yaml` (and optional mounted copy for identity-service).

## Schema (version 1)

```yaml
adminRbacOverrides:
  version: 1
  assignments:
    - id: "<optional-stable-id>"
      userId: "<uuid>"
      role: "PLATFORM_AUDIT_EXPORTER"
      action: "GRANT"   # or REVOKE
      reasonRef: "change-request:<uuid>"
      requestedBy: "<uuid-or-empty>"
      approvedBy: "<uuid-or-empty>"
      status: "APPROVED_FOR_APPLY"  # Faz 88: DISABLED ignored; APPLIED/REVOKED/EXPIRED reserved
      expiresAt: null               # ISO-8601 instant; past → row ignored (warning)
      createdAt: "2026-01-01T00:00:00Z"
      metadata:
        source: "gitops"
```

## Faz 88 parser rules

- Ingested statuses: **`APPROVED_FOR_APPLY`** only. **`DISABLED`** rows are skipped.
- Unknown role, invalid UUID, missing `reasonRef`, expired `expiresAt`, or unknown user (when loader validates DB) → row ignored with warnings.
- `metadata.source` defaults to `gitops` when omitted.

PR automation from Faz 87 emits compatible rows (`id`, `createdAt`, `metadata.source`).
