# Enterprise admin write operations (Faz 77 + Faz 78)

This work adds a **safe write foundation** for the Enterprise Admin Console: **admin change requests** stored in `identity-service`. The console does **not** mutate live runtime configuration or secrets. **Faz 78** adds **approve/reject** and four-eyes policy without automatic apply.

## Why change requests instead of direct config edits

- **Blast radius**: many flags affect authentication, SCIM, merge apply, or admin surfaces; direct mutation from a browser is high risk.
- **Auditability**: every validate/create/cancel emits structured audit metadata (no secret values).
- **GitOps alignment**: requested state is recorded as `PENDING`; operators apply changes via Helm/GitOps or runbooks. Automatic apply is out of scope for Faz 77.

## Feature flags

| Variable | Service | Purpose |
|----------|---------|---------|
| `GATEWAY_ADMIN_WRITE_ENABLED` | api-gateway | Enables `/admin/enterprise/change-requests/**` when enterprise admin is also enabled. |
| `GATEWAY_ADMIN_CHANGE_REQUESTS_INTERNAL_PATH` | api-gateway | Path on identity-service (default `/internal/admin/change-requests`). |
| `GATEWAY_ADMIN_WRITE_RATE_LIMIT_*` | api-gateway | Redis bucket for change-request routes. |
| `ADMIN_CHANGE_REQUESTS_ENABLED` | identity-service | Persists and serves internal change-request API. |
| `ADMIN_CHANGE_REQUEST_RETENTION_DAYS` | identity-service | Retention hint for operators (purge job future work). |
| `ADMIN_CHANGE_REQUEST_APPROVALS_ENABLED` | identity-service | Enables approve/reject paths (see [admin-change-request-approval-workflow.md](./admin-change-request-approval-workflow.md)). |
| `ADMIN_CHANGE_REQUEST_REQUIRE_DIFFERENT_APPROVER` | identity-service | Four-eyes: creator cannot approve own request when true. |
| `ADMIN_CHANGE_REQUEST_LOW_SEVERITY_AUTO_APPROVE` | identity-service | Optional auto-approve on create for low severity (default false). |
| `ADMIN_CHANGE_REQUEST_REJECT_REASON_REQUIRED_FOR_HIGH` | identity-service | Reject requires reason for HIGH severity when true. |
| `FRONTEND_ENTERPRISE_ADMIN_WRITE_ENABLED` | frontend | Shows change-request UI and deep links from status cards. |
| `FRONTEND_ENTERPRISE_ADMIN_APPROVALS_ENABLED` | frontend | Shows approve/reject UI (defaults on when write UI is on). |

Defaults: **disabled** in Helm `values.yaml`; **enabled** in GitOps `dev` overlay for local-style environments.

## Public gateway API (browser)

All routes require an authenticated **platform admin** JWT. **Email/user allowlists are not sufficient** for writes: `PLATFORM_ADMIN` (or equivalent role claims) is required. **MFA** is required whenever gateway admin MFA mode is not `off` or `gateway.admin.require-mfa` is true.

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/admin/enterprise/change-requests` | List recent requests (optional `?status=` filter: `PENDING`, `APPROVED`, `REJECTED`, `CANCELLED`, or omit for all). |
| `POST` | `/admin/enterprise/change-requests/validate` | Dry-run validation + impact summary. |
| `POST` | `/admin/enterprise/change-requests` | Create `PENDING` request. |
| `POST` | `/admin/enterprise/change-requests/{id}/cancel` | Cancel own `PENDING` request. |
| `POST` | `/admin/enterprise/change-requests/{id}/approve` | Approve `PENDING` request (four-eyes when configured). |
| `POST` | `/admin/enterprise/change-requests/{id}/reject` | Reject `PENDING` request. |

Cookie transports: unsafe methods use **double-submit CSRF** (same as other gateway mutations).

## Allow-listed operations

Defined in `identity-service` `AdminOperationRegistry`:

- `ADMIN_MFA_MODE_UPDATE` — gateway admin MFA mode (`off` / `observe` / `warn` / `enforce`).
- `MERGE_ANALYSIS_ROLLOUT_REQUEST` — `NOTE_MERGE_ANALYSIS_ENABLED` desired state (`true` / `false`).
- `MERGE_APPLY_ROLLOUT_REQUEST` — `NOTE_MERGE_APPLY_ENABLED` desired state (`true` / `false`).
- `SCIM_BULK_ROLLOUT_REQUEST` — `SCIM_BULK_ENABLED` desired state (`true` / `false`).

`runtimeApplySupported` is **false** for all operations in this phase; apply via GitOps.

## Error codes (representative)

| Code | When |
|------|------|
| `ADMIN_WRITE_DISABLED` | Gateway or identity feature disabled. |
| `ADMIN_WRITE_MFA_REQUIRED` | Platform admin JWT without satisfied MFA while MFA policy requires it for writes. |
| `ADMIN_OPERATION_NOT_ALLOWED` | Operation not on allow-list. |
| `ADMIN_CHANGE_REQUEST_INVALID` | Bad payload, value, or missing `CONFIRM` for `HIGH` severity. |
| `ADMIN_CHANGE_REQUEST_NOT_FOUND` | Cancel target missing or not owned by caller. |
| `ADMIN_CHANGE_REQUEST_NOT_CANCELLABLE` | Not `PENDING`. |
| `ADMIN_CHANGE_REQUEST_NOT_PENDING` | Approve/reject when not `PENDING`. |
| `ADMIN_CHANGE_REQUEST_SELF_APPROVAL_NOT_ALLOWED` | Approver is creator while different approver is required. |
| `ADMIN_CHANGE_REQUEST_APPROVAL_DISABLED` | Identity approvals feature off. |
| `ADMIN_CHANGE_REQUEST_REJECT_REASON_REQUIRED` | Reject reason missing for HIGH severity. |

## Internal identity API

`identity-service` exposes `/internal/admin/change-requests` secured with **service JWT** scope `internal:admin:change-requests:manage` (see `AuditAdminAuthorizer`). The browser never sees this token.

## Related docs

- [admin-change-request-workflow.md](./admin-change-request-workflow.md)
- [admin-change-request-approval-workflow.md](./admin-change-request-approval-workflow.md)
- [enterprise-admin-console.md](./enterprise-admin-console.md)
