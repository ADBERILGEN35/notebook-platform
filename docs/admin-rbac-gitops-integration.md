# Admin RBAC GitOps integration (Faz 87)

## Purpose

Approved `ADMIN_RBAC_ROLE_GRANT_REQUEST` and `ADMIN_RBAC_ROLE_REVOKE_REQUEST` change requests can generate **GitOps pull requests** that append a governance row to:

`deploy/gitops/environments/{env}/admin-rbac-overrides.yaml`

This file is an **approved desired-state proposal**. **Faz 87:** identity-service did not ingest it at runtime. **Faz 88:** optional, flag-gated ingestion from a **mounted copy** of the manifest may merge rows into effective `platform_roles` (see [admin-rbac-runtime-overrides.md](./admin-rbac-runtime-overrides.md)); defaults remain **off** in production. IdP/SCIM automation is still out of scope.

## Flags

| Layer | Variable | Default |
| --- | --- | --- |
| identity-service | `ADMIN_GITOPS_RBAC_ROLE_REQUESTS_ENABLED` | `false` |
| Frontend runtime | `FRONTEND_GITOPS_RBAC_ROLE_REQUESTS_ENABLED` → `GITOPS_RBAC_ROLE_REQUESTS_ENABLED` in `__NOTEBOOK_CONFIG__` | `false` |

RBAC GitOps actions also require the Faz 80 pair: `ADMIN_GITOPS_PR_ENABLED` and `FRONTEND_ENTERPRISE_GITOPS_PR_ENABLED` (with enterprise admin write).

## Security

- No secrets, SCIM tokens, SSO client secrets, or raw IdP claims in PR bodies or audit metadata for this flow.
- PRs reference `userId`, `role`, `action`, and `reasonRef: change-request:<uuid>` only.
- `PLATFORM_ADMIN` grant/revoke remains **HIGH** severity (existing change-request + prod `CONFIRM` rules apply to GitOps PR creation).

## Duplicate handling

Dry-run may return warning `RBAC_ASSIGNMENT_ALREADY_PROPOSED` when the baseline manifest already contains the same `userId` + `role` + `action`. This is a warning, not a hard failure.

## Rollback

Revert the appended assignment in `admin-rbac-overrides.yaml` via a follow-up PR.
