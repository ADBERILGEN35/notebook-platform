# Admin RBAC runtime overrides (Faz 88)

## Summary

`identity-service` can optionally **read** a mounted `admin-rbac-overrides.yaml` manifest and merge assignments into **effective `platform_roles`** (tokens) and the **admin RBAC visibility** API. Defaults are **off** in all environments until explicitly enabled after governance review.

## Flags (identity-service)

| Property / env | Default | Meaning |
| --- | --- | --- |
| `ADMIN_RBAC_OVERRIDES_ENABLED` | `false` | Master switch for ingestion |
| `ADMIN_RBAC_OVERRIDES_FILE` | `/etc/notebook/admin-rbac-overrides/admin-rbac-overrides.yaml` | Path to YAML file |
| `ADMIN_RBAC_OVERRIDES_FAIL_CLOSED` | `false` | If `true` and file missing/invalid → process fails startup |
| `ADMIN_RBAC_OVERRIDES_MAX_ASSIGNMENTS` | `500` | Raw row cap per file |
| `ADMIN_RBAC_OVERRIDES_REQUIRE_APPROVED_STATUS` | `true` | Reserved; parser only applies `APPROVED_FOR_APPLY` rows in Faz 88 |

## REVOKE semantics

`REVOKE` only pairs with prior **override `GRANT` stack depth** for that user/role. It **does not** remove roles that exist solely because of SSO/SCIM group mappings (warning `OVERRIDE_REVOKE_IGNORED_NON_OVERRIDE_ROLE` in visibility).

## Operations

- **No hot reload**: file is read at startup (`@PostConstruct`). Pod restart / rollout picks up new manifest.
- **Status / validate** internal APIs are proxied via gateway (`admin:rbac:read`). Validate accepts YAML **content** but does **not** persist it.

See also [admin-rbac-override-manifest.md](./admin-rbac-override-manifest.md) and [admin-rbac-gitops-integration.md](./admin-rbac-gitops-integration.md).
