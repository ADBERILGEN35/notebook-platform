# Admin GitOps PR automation (Faz 80)

Approved **platform admin change requests** can drive **allow-listed** GitOps pull requests. Identity-service computes a **secret-safe** YAML patch plan (dry-run) and optionally opens a PR via a **mock** or **GitHub** provider. **Runtime config is never mutated** by this flow, and **auto-merge is not supported**.

## Feature flags and config

**identity-service** (`application.yml` / env):

| Variable | Purpose |
|----------|---------|
| `ADMIN_GITOPS_PR_ENABLED` | Master switch (`identity.admin.gitops.enabled`) |
| `ADMIN_GITOPS_PROVIDER` | `mock` or `github` |
| `ADMIN_GITOPS_ALLOWED_ENVIRONMENTS` | Comma list, e.g. `dev,staging,prod` |
| `ADMIN_GITOPS_DEFAULT_ENVIRONMENT` | Default for validate preview when omitted |
| `ADMIN_GITOPS_BASE_BRANCH` | PR base branch |
| `ADMIN_GITOPS_BRANCH_PREFIX` | Head branch prefix |
| `ADMIN_GITOPS_REPOSITORY_OWNER` / `ADMIN_GITOPS_REPOSITORY_NAME` | GitHub repo (provider `github`) |
| `ADMIN_GITOPS_GITHUB_TOKEN` | Server-side PAT (never returned to clients) |
| `ADMIN_GITOPS_REQUIRE_APPROVED_CHANGE` | When `true`, GitOps endpoints require `APPROVED` status |

**Frontend**: `FRONTEND_ENTERPRISE_GITOPS_PR_ENABLED` (requires enterprise write UI).

**Gateway**: GitOps routes live under `/admin/enterprise/change-requests/...`; they use the same **admin write** rate limit bucket as other change-request mutations.

## API (via gateway)

- `POST /admin/enterprise/change-requests/{id}/gitops/dry-run`  
  Body: `{ "targetEnvironment": "staging" }` (optional; defaults to the change request’s environment).  
  Permission: `admin:change-request:gitops:dry-run` (or `PLATFORM_ADMIN` when RBAC is off).

- `POST /admin/enterprise/change-requests/{id}/gitops/create-pr`  
  Body: `{ "targetEnvironment": "staging", "idempotencyKey": "<uuid>", "confirmation": "CONFIRM" }`  
  `targetEnvironment` must match the change request. **Prod + HIGH** severity requires `confirmation: CONFIRM`.  
  Permission: `admin:change-request:gitops:create` + **admin MFA** when gateway policy requires MFA for writes.

## Mapping registry

Only these operations are mapped; arbitrary paths are rejected:

| Operation | `values.yaml` path (under `config`) |
|-------------|-------------------------------------|
| `ADMIN_MFA_MODE_UPDATE` | `gatewayAdminMfaMode` |
| `MERGE_ANALYSIS_ROLLOUT_REQUEST` | `noteMergeAnalysisEnabled` |
| `MERGE_APPLY_ROLLOUT_REQUEST` | `noteMergeApplyEnabled` |
| `SCIM_BULK_ROLLOUT_REQUEST` | `scimBulkEnabled` |

File target: `deploy/gitops/environments/{env}/values.yaml`.

Dry-run uses **packaged baselines** under `identity-service/src/main/resources/gitops-baselines/{env}/values.yaml` for preview. The GitHub provider loads the **live** file from the repo before patching.

## Persistence

Table `admin_gitops_pr_proposals` stores proposal metadata, changed file JSON, optional idempotency key, and PR URL/number. Partial unique index: at most one `PR_CREATED` row per `(change_request_id, target_environment, provider)`.

## Audit events

- `ADMIN_GITOPS_DRY_RUN_CREATED`
- `ADMIN_GITOPS_PR_CREATED`
- `ADMIN_GITOPS_PR_CREATION_FAILED`
- `ADMIN_GITOPS_PROVIDER_VALIDATION_FAILED`

Metadata includes change request id, operation, environment, provider, paths, branch, PR URL — **never** tokens or secret values.

## Error codes (identity)

`ADMIN_GITOPS_DISABLED`, `ADMIN_GITOPS_CHANGE_REQUEST_NOT_APPROVED`, `ADMIN_GITOPS_OPERATION_UNSUPPORTED`, `ADMIN_GITOPS_ENVIRONMENT_NOT_ALLOWED`, `ADMIN_GITOPS_MAPPING_NOT_FOUND`, `ADMIN_GITOPS_PATCH_FAILED`, `ADMIN_GITOPS_PROVIDER_FAILED`, `ADMIN_GITOPS_PR_ALREADY_EXISTS` (reserved / returned as existing success where applicable), `ADMIN_GITOPS_IDEMPOTENCY_KEY_REUSED`, and gateway `ADMIN_PERMISSION_REQUIRED` / `ADMIN_WRITE_MFA_REQUIRED` as appropriate.

## Out of scope (Faz 80)

Runtime apply, auto-merge, arbitrary YAML editing, secret value changes, drift reconciliation, Terraform/Pulumi, multi-repo orchestration.

See also: [admin-gitops-provider-github.md](./admin-gitops-provider-github.md).
