# Admin change request workflow

## Actors

- **Platform admin** or fine-grained roles with the permissions in [`docs/admin-permission-matrix.md`](admin-permission-matrix.md) (Faz 79: JWT `platform_permissions` when RBAC is enabled).
- **API gateway** — authenticates the admin, enforces MFA policy for writes, issues **service JWT** to identity.
- **Identity service** — allow-list validation, persistence, audit events.

## Flow

1. **Validate (dry-run)**  
   `POST /admin/enterprise/change-requests/validate` with `operationType`, `requestedValue`, optional `currentValue`, optional `targetEnvironment` (defaults to `ADMIN_GITOPS_DEFAULT_ENVIRONMENT`).  
   Response includes `impactSummary` (severity, description, rollback hint, affected surfaces) and `validationResult`.

2. **Review impact**  
   High-severity operations require typing `CONFIRM` in the UI before create.

3. **Create**  
   `POST /admin/enterprise/change-requests` creates a row in `platform_admin_change_requests` with status `PENDING`.  
   No automatic apply to application YAML, env, or secrets.

4. **Approve / reject (Faz 78)**  
   A **different** platform admin (when `ADMIN_CHANGE_REQUEST_REQUIRE_DIFFERENT_APPROVER=true`) may  
   `POST /admin/enterprise/change-requests/{id}/approve` or `.../reject`.  
   Approving does **not** apply config; it records **APPROVED** and audit for GitOps/runbook handoff.  
   See [admin-change-request-approval-workflow.md](./admin-change-request-approval-workflow.md).

5. **Operator handoff (Faz 80)**  
   For **APPROVED** requests, operators may use GitOps PR automation (`POST .../gitops/dry-run`, `POST .../gitops/create-pr`) when enabled — see [admin-gitops-pr-automation.md](./admin-gitops-pr-automation.md). Otherwise update GitOps / runbooks manually.

6. **Cancel**  
   The requesting admin may `POST .../{id}/cancel` while status is `PENDING`.

## Audit events (identity)

- `ADMIN_CHANGE_REQUEST_VALIDATED`
- `ADMIN_CHANGE_REQUEST_CREATED`
- `ADMIN_CHANGE_REQUEST_CANCELLED`
- `ADMIN_CHANGE_REQUEST_APPROVED` / `ADMIN_CHANGE_REQUEST_REJECTED` / `ADMIN_CHANGE_REQUEST_APPROVAL_DENIED` (Faz 78)
- `ADMIN_GITOPS_*` (Faz 80; see GitOps doc)

Metadata includes operation type, target service/key, requested value, severity; **never** raw secrets.

## Out of scope

- Automatic apply, GitOps PR bots, arbitrary YAML editing, secret rotation UI, multi-level approvals (see approval doc for Faz 78 scope).
