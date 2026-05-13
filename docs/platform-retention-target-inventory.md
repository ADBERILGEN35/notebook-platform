# Platform Retention Target Inventory (Faz 98)

Faz 98 platform genelinde veri siniflarini envantere alir. Bu dosya destructive purge sozlesmesi degildir; target durumlari `INVENTORY_ONLY` veya `DRY_RUN_READY` olarak baslar.

| Target | Service | Data class | Default retention | Legal hold | Destructive purge | Archive required | Risk | Status |
|--------|---------|------------|-------------------|------------|-------------------|------------------|------|--------|
| `content.workspaces` | workspace-service | CONTENT | Policy-defined | yes | no | yes | CRITICAL | INVENTORY_ONLY |
| `content.notebooks` | workspace-service | CONTENT | Policy-defined | yes | no | yes | HIGH | INVENTORY_ONLY |
| `content.notes` | content-service | CONTENT | Policy-defined | yes | no | yes | CRITICAL | INVENTORY_ONLY |
| `content.note_versions` | content-service | CONTENT | 365d | yes | no | yes | HIGH | INVENTORY_ONLY |
| `content.comments` | content-service | CONTENT | 365d | yes | no | yes | HIGH | INVENTORY_ONLY |
| `content.attachments_media` | object-storage | CONTENT | Policy-defined | yes | no | yes | CRITICAL | INVENTORY_ONLY |
| `search.documents` | search-service | CONTENT | 90d | yes | no | no | MEDIUM | INVENTORY_ONLY |
| `identity.users` | identity-service | IDENTITY | Policy-defined | yes | no | yes | CRITICAL | INVENTORY_ONLY |
| `identity.external_identities` | identity-service | IDENTITY | Policy-defined | yes | no | yes | HIGH | INVENTORY_ONLY |
| `identity.scim_users_groups` | identity-service | IDENTITY | Policy-defined | yes | no | yes | HIGH | INVENTORY_ONLY |
| `identity.scim_sync_runs` | identity-service | IDENTITY | 180d | yes | no | no | MEDIUM | DRY_RUN_READY |
| `identity.sessions_refresh_tokens` | identity-service | IDENTITY | 90d | yes | no | no | HIGH | INVENTORY_ONLY |
| `audit.events` | identity/content/notification services | AUDIT_SECURITY | 365d | yes | no | yes | CRITICAL | INVENTORY_ONLY |
| `audit.break_glass_events` | identity-service | AUDIT_SECURITY | 365d | yes | no | yes | CRITICAL | INVENTORY_ONLY |
| `audit.admin_change_requests` | identity-service | AUDIT_SECURITY | 180d | yes | no | yes | HIGH | INVENTORY_ONLY |
| `audit.admin_rbac_overrides` | identity-service | AUDIT_SECURITY | Policy-defined | yes | no | yes | HIGH | INVENTORY_ONLY |
| `identity.mfa_webauthn_metadata` | identity-service | AUDIT_SECURITY | Policy-defined | yes | no | yes | HIGH | INVENTORY_ONLY |
| `notification.retention_targets` | notification-service | NOTIFICATION | 90d | yes | no | no | MEDIUM | DRY_RUN_READY |

## Status meanings

- `INVENTORY_ONLY`: listed for governance visibility; no row count or purge implementation.
- `DRY_RUN_READY`: included in dry-run planning; destructive purge still disabled.
- `PURGE_READY`: reserved for future explicit approval.
- `DISABLED`: intentionally not planned.
