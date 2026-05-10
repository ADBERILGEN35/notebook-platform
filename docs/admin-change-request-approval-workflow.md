# Admin change request approval workflow (Faz 78)

This document describes the **four-eyes (two-person)** approval path for enterprise admin change requests created in Faz 77. It does **not** cover GitOps PR automation or runtime apply (future phases).

## Goals

- Separate **submission** from **approval**: the creator must not be the sole approver when `ADMIN_CHANGE_REQUEST_REQUIRE_DIFFERENT_APPROVER=true`.
- Keep **no automatic apply**: approving only updates status and audit; operators still change config via GitOps or runbooks.
- Preserve **security controls**: platform admin role, gateway MFA policy for writes, CSRF on mutating browser calls, service JWT for internal identity calls, no secret material in audit metadata.

## Status model (this phase)

Active transitions implemented in Faz 78:

| From     | To         | Actor / note                                      |
|----------|------------|---------------------------------------------------|
| PENDING  | APPROVED   | Different platform admin (when policy requires)   |
| PENDING  | REJECTED   | Different platform admin (when policy requires)   |
| PENDING  | CANCELLED  | Requesting admin only (existing cancel endpoint)  |

Terminal: **REJECTED**, **CANCELLED**. **APPROVED** is followed by manual/GitOps apply only.

**APPLIED** / **FAILED** remain reserved for a future apply/GitOps phase.

## Policy configuration (identity-service)

| Environment variable | Meaning |
|---------------------|---------|
| `ADMIN_CHANGE_REQUEST_APPROVALS_ENABLED` | Master switch for approve/reject internal + gateway routes (when disabled → `ADMIN_CHANGE_REQUEST_APPROVAL_DISABLED`). |
| `ADMIN_CHANGE_REQUEST_REQUIRE_DIFFERENT_APPROVER` | If true, creator cannot approve/reject their own request (four-eyes). |
| `ADMIN_CHANGE_REQUEST_REQUIRE_MFA_FOR_APPROVAL` | Documented for future tightening; gateway already enforces MFA for admin writes when policy requires it. |
| `ADMIN_CHANGE_REQUEST_HIGH_SEVERITY_REQUIRES_APPROVAL` | Reserved / aligned with validation; high severity always expects human review. |
| `ADMIN_CHANGE_REQUEST_LOW_SEVERITY_AUTO_APPROVE` | If true, identity may auto-approve on create for low severity (use with care). |
| `ADMIN_CHANGE_REQUEST_REJECT_REASON_REQUIRED_FOR_HIGH` | If true, reject without reason fails for HIGH severity. |

Suggested production posture: **approvals on**, **different approver on**, **low auto-approve off**, **reject reason for HIGH on**. Helm defaults keep approvals **off** until operators enable them alongside `ADMIN_CHANGE_REQUESTS_ENABLED` and gateway admin write.

## APIs

### Gateway (browser)

- `POST /admin/enterprise/change-requests/{id}/approve` — body optional `{ "reason": "..." }`.
- `POST /admin/enterprise/change-requests/{id}/reject` — body `{ "reason": "..." }` (reason required for HIGH when configured).

Same auth as other change-request writes: **PLATFORM_ADMIN**, admin write enabled, **MFA** when gateway policy requires, **CSRF** for cookie sessions, admin-write **rate limit** bucket.

### Identity (internal)

- `POST /internal/admin/change-requests/{id}/approve`
- `POST /internal/admin/change-requests/{id}/reject`

Service JWT scope: `internal:admin:change-requests:manage`. Headers: `X-Admin-User-Id`, `X-Admin-User-Email`, `X-Request-Id`.

### Representative error codes

| Code | Meaning |
|------|---------|
| `ADMIN_CHANGE_REQUEST_APPROVAL_DISABLED` | Approvals feature off in identity. |
| `ADMIN_CHANGE_REQUEST_NOT_PENDING` | Request not in PENDING state. |
| `ADMIN_CHANGE_REQUEST_SELF_APPROVAL_NOT_ALLOWED` | Four-eyes: approver is creator. |
| `ADMIN_CHANGE_REQUEST_REJECT_REASON_REQUIRED` | Missing reason for HIGH rejection. |
| `ADMIN_WRITE_MFA_REQUIRED` | Gateway MFA not satisfied for write. |

## Audit events (identity)

- `ADMIN_CHANGE_REQUEST_APPROVED`
- `ADMIN_CHANGE_REQUEST_REJECTED`
- `ADMIN_CHANGE_REQUEST_APPROVAL_DENIED` (e.g. self-approval blocked)

Metadata includes operation, targets, severity, user ids, whether a decision reason was present, and **never** raw secrets.

## Approve response and handoff

Approve returns `nextStep` with `type: GITOPS_OR_MANUAL_APPLY` and a message that apply is not automatic. Operators should follow `docs/admin-change-request-workflow.md` and your platform runbook.

## Break-glass and limitations (Faz 78)

- **Break-glass**: if a second platform admin is unavailable, operators may temporarily set `ADMIN_CHANGE_REQUEST_REQUIRE_DIFFERENT_APPROVER=false` via GitOps (with change control), complete approvals, then restore the stricter setting. This is an organizational/process decision, not a UI override.
- **Not in this phase**: multiple approvers, role-based approval routing, emergency override tokens, delegation, fine-grained admin RBAC, automatic GitOps PRs, runtime apply from the console.

## Related documents

- [admin-change-request-workflow.md](./admin-change-request-workflow.md) — validate / create / cancel / audit baseline.
- [enterprise-admin-write-operations.md](./enterprise-admin-write-operations.md) — flags and public API summary.
- [admin-mfa-enforcement.md](./admin-mfa-enforcement.md) — MFA for admin writes.
